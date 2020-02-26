package pers.liuqi.rpc;

import com.egls.server.utils.CollectionUtil;
import com.egls.server.utils.databind.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import pers.liuqi.rpc.config.Constant;
import pers.liuqi.rpc.service.ServiceInfo;
import pers.liuqi.rpc.util.LauncherProperties;
import pers.liuqi.rpc.util.RuntimeLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * <pre>
 * 服务治理模块，使用Zookeeper作为注册中心.
 *
 * 此模块通过注册中心来监控所有服务提供者的服务状态变化.
 * 并通过这些状态以及从注册中心获取到的数据来构建本地服务代理.
 *
 * Zookeeper注册中心存储数据的结构是以一个树形的形式存储的.
 * 对于一个实际的RPC服务信息，路径会是以下这种三层结构.
 *
 *
 * 根路径|     服务名称       |    不同的服务实例
 * ==================================================
 *                                    | ---- server1[data]
 *                | ---- friendService| ---- server2[data]
 *                |                   | ---- server3[data]
 *  game -- rpc --|
 *                |
 *                |                   | ---- server1[data]
 *                | ---- chatService  | ---- server2[data]
 *                                    | ---- server3[data]
 *
 *
 * Zookeeper中节点主要分为两种类型
 * 持久化节点：不主动删除的话永久存在，即Zookeeper重启也会存在的节点.
 * 临时节点：  是在Zookeeper客户端连接的Session过期后
 *
 * 在整个树形结构中，所有非叶节点都是持久化节点，所有叶节点可以在创建时指定其类型.
 * 由于临海节点的特性，在网络闪断时，即使服务节点本身正常提供服务，也有可能会被Zookeeper删除掉，从而导致调用者认为其已不可用.
 * 所以存储服务信息的叶节点使用持久化类型节点，由服务本身决定何时通知注册中心其不可用.
 * 这样即使Zookeeper服务出现异常，调用者只要未收到注册中心的通知，并且提供者可以连通，就可以继续使用其服务
 *
 *
 * 同一种服务，可以同时有多个服务实例来共同提供服务，也可以是单一服务实例，具体使用哪个服务由使用者来决定.
 * 在修改注册中心的节点数据时，需要使用一个类似下边这种路径来指定操作的节点.
 * /rpc/serviceName/serverId
 *
 * </pre>
 *
 * @author LiuQi - [Created on 2018-08-10]
 */
class ServiceInfoManager implements TreeCacheListener {

    private static final int DATA_PATCH_LENGTH = 4;

    private CuratorFramework client;

    public void start(String zkAddress) throws Exception {

        //启动客户端
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(5000, 10);
        client = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .sessionTimeoutMs(5000)
                .retryPolicy(retryPolicy)
                .build();
        client.start();

        //创建根节点
        if (client.checkExists().forPath(getRootPath()) == null) {
            client.create().creatingParentsIfNeeded().forPath(getRootPath());
        }

        //开始构建服务代理
        buildServiceProxy();

        //监听节点变化
        TreeCache treeCache = new TreeCache(client, getRootPath());
        treeCache.start();
        treeCache.getListenable().addListener(this);

    }

    void shutdown() {
        client.close();
    }

    /**
     * 注册服务
     */
    void registerService(ServiceInfo info) throws Exception {
        String path = getPath(info.getName(), info.getId());
        byte[] data = JsonObject.serialize(info).getBytes();

        if (client.checkExists().creatingParentsIfNeeded().forPath(path) != null) {
            client.setData().forPath(path, data);
        } else {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path, data);
        }
        RuntimeLogger.info("[Zookeeper] register service", info.getName(), info.getId());
    }

    /**
     * 取消注册
     */
    void unRegisterService(String serviceName, String serverId) {
        try {
            String path = getPath(serviceName, serverId);
            if (client.checkExists().forPath(path) != null) {
                client.delete().forPath(path);

                RuntimeLogger.info("[Zookeeper] unregister service", serviceName, serverId);
            }
        } catch (Exception e) {
            RuntimeLogger.error(e);
        }
    }

    /**
     * 构建服务调用的代理接口
     */
    private void buildServiceProxy() {
        RuntimeLogger.info("[Zookeeper] build service proxy");

        List<ServiceInfo> serviceList = getServiceList();
        if (serviceList != null) {
            for (ServiceInfo info : serviceList) {
                if (info.isAvailable()) {
                    //生成远程服务调用接口
                    createServiceProxyIfNotExist(info);
                } else {
                    //销毁不可用服务
                    destroyProxy(info);
                }
            }
        }
    }

    /**
     * 构建一个服务的代理接口, 包括本地服务代理和远程服务代理
     */
    private void createServiceProxyIfNotExist(ServiceInfo info) {
        Class<?> serviceClass = getServiceClass(info);
        if (serviceClass == null) {
            return;
        }

        try {
            Map<Class<?>, Map<String, Object>> serviceProxyMap = RpcContext.getServiceProxyMap();
            Map<String, Object> serviceMap = serviceProxyMap.computeIfAbsent(serviceClass, key -> CollectionUtil.newHashMap());
            if (!serviceMap.containsKey(info.getId())) {
                Object caller = createServiceProxy(info);
                if (caller != null) {
                    serviceMap.put(info.getId(), caller);
                }
            }
        } catch (Exception e) {
            RuntimeLogger.error(String.format("build remote service caller error serviceInfo -> %s", info.toString()), e);
        }
    }

    private Object createServiceProxy(ServiceInfo info) {
        Class<?> serviceClass = getServiceClass(info);
        if (serviceClass == null) {
            return null;
        }

        try {

            //本地服务代理
            if (RpcServiceStarter.getSingleton().isLocalServer(info.getId())) {
                AbstractServiceProvider invoker = RpcContext.getProvider(info.getName());
                return DynamicClassUtil.buildLocalProxy(serviceClass, invoker, info);
            }

            //远程服务代理
            return DynamicClassUtil.buildRemoteProxy(info);
        } catch (Exception e) {
            RuntimeLogger.error(String.format("build remote service caller error serviceInfo -> %s", info.toString()), e);
        }
        return null;
    }

    /**
     * 销毁一个服务代理
     */
    private void destroyProxy(ServiceInfo info) {
        Class<?> serviceClass = getServiceClass(info);
        if (serviceClass == null) {
            return;
        }

        Map<Class<?>, Map<String, Object>> serviceProxyMap = RpcContext.getServiceProxyMap();
        Map<String, Object> serviceMap = serviceProxyMap.computeIfAbsent(serviceClass, key -> CollectionUtil.newHashMap());
        Object serviceProxy = serviceMap.remove(info.getId());
        if (serviceProxy instanceof AbstractServiceRemoteProxy) {
            ((AbstractServiceRemoteProxy) serviceProxy).destroy();
        }
    }

    private Class<?> getServiceClass(ServiceInfo info) {
        try {
            return Class.forName(info.getServiceInterfaceName());
        } catch (ClassNotFoundException e) {
            RuntimeLogger.error(e);
            return null;
        }
    }

    /**
     * 获取服务列表
     */
    private List<ServiceInfo> getServiceList() {
        RuntimeLogger.info("[Zookeeper] fetch service list");


        //获取服务列表
        List<String> serviceNameList;
        try {
            serviceNameList = client.getChildren().forPath(getRootPath());
        } catch (Exception e) {
            RuntimeLogger.info("[Zookeeper] fetch service list fail", e);
            return null;
        }

        List<ServiceInfo> serviceInfoList = new ArrayList<>();
        for (String serviceName : serviceNameList) {
            Map<String, ServiceInfo> infoMap = CollectionUtil.newHashMap();

            //获取当前服务的所有提供者
            List<String> serverList;
            try {
                serverList = client.getChildren().forPath(getPath(serviceName));
            } catch (Exception e) {
                RuntimeLogger.error(String.format("[Zookeeper] fetch server list fail. service -> (%s)", serviceName));
                continue;
            }

            for (String serverId : serverList) {
                //获取每个提供者具体的数据
                try {
                    String dataStr = new String(client.getData().forPath(getPath(serviceName, serverId)));
                    ServiceInfo info = JsonObject.deserialize(dataStr, ServiceInfo.class);
                    infoMap.put(serverId, info);
                } catch (Exception e) {
                    RuntimeLogger.error(String.format("[Zookeeper] fetch service data fail. service -> (%s), serverId -> (%s)", serviceName, serverId));
                }
            }
            serviceInfoList.addAll(infoMap.values());
        }

        RuntimeLogger.info("[Zookeeper] service info list: " + serviceInfoList);
        return serviceInfoList;
    }

    @Override
    public void childEvent(CuratorFramework client, TreeCacheEvent event) {
        switch (event.getType()) {
            case NODE_ADDED: {
                ServiceInfo info = getEventData(event);
                if (info != null) {
                    RuntimeLogger.info(String.format("[Zookeeper] Node add path -> (%s), info -> (%s)", event.getData().getPath(), info));

                    //服务可用的情况下，构建代理接口
                    if (info.isAvailable()) {
                        createServiceProxyIfNotExist(info);
                    }
                }
                break;
            }
            case NODE_REMOVED: {
                ServiceInfo info = getEventData(event);
                if (info != null) {
                    RuntimeLogger.info(String.format("[Zookeeper] Node remove path -> (%s), info -> (%s)", event.getData().getPath(), info));

                    //销毁服务代理
                    destroyProxy(info);
                }
                break;
            }
            case NODE_UPDATED: {
                ServiceInfo info = getEventData(event);
                if (info != null) {
                    RuntimeLogger.info(String.format("[Zookeeper] Node update path -> (%s), info -> (%s)", event.getData().getPath(), info));

                    //不可用时销毁服务代理
                    if (!info.isAvailable()) {
                        destroyProxy(info);
                        break;
                    }

                    //todo 本地服务不可用时，设置为可用
                    //本地还没有该服务的接口，或者服务接口变化了，重新构建一下
                    Class<?> serviceInterface = getServiceClass(info);
                    if (serviceInterface != null) {
                        Object serviceProxy = getServiceProxy(info, serviceInterface);
                        if (serviceProxy == null || !serviceInterface.isAssignableFrom(serviceProxy.getClass())) {
                            destroyProxy(info);
                            createServiceProxyIfNotExist(info);
                        }
                    }


                }
                break;
            }
            default:

        }
    }

    private Object getServiceProxy(ServiceInfo info, Class<?> serviceInterfaceClass) {
        Map<Class<?>, Map<String, Object>> serviceProxyMap = RpcContext.getServiceProxyMap();
        Map<String, Object> serviceMap = serviceProxyMap.get(serviceInterfaceClass);
        if (serviceMap == null) {
            return null;
        }
        return serviceMap.get(info.getId());
    }

    private ServiceInfo getEventData(TreeCacheEvent event) {
        //验证是否是服务节点的变化
        //如果是服务节点变化，路径一定是三层的结构，并且根节点相匹配
        String[] pathList = StringUtils.split(event.getData().getPath(), "/");
        if (pathList.length != DATA_PATCH_LENGTH) {
            return null;
        }

        if (event.getData().getData() == null) {
            return null;
        }

        //解析节点数据
        ServiceInfo info;
        try {
            String dataStr = new String(event.getData().getData());
            info = JsonObject.deserialize(dataStr, ServiceInfo.class);
        } catch (Exception e) {
            RuntimeLogger.error("deserialize service info error", e);
            return null;
        }

        //验证节点数据
        String serviceName = pathList[1];
        String serverId = pathList[2];
        if (!StringUtils.equals(serviceName, info.getName()) || !StringUtils.equals(serverId, info.getId())) {
            RuntimeLogger.error(String.format("service data verify fail path -> (%s), data -> (%s)", event.getData().getPath(), info));
            return null;
        }
        return info;
    }

    private String getPath(String serviceName) {
        return getRootPath() + "/" + serviceName;
    }

    private String getPath(String serviceName, String serverId) {
        return getRootPath() + "/" + serviceName + "/" + serverId;
    }

    private String getRootPath() {
        return LauncherProperties.game + "/" + Constant.ZOOKEEPER_ROOT_PATH;
    }
}
