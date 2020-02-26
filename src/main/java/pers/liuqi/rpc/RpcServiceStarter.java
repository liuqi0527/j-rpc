package pers.liuqi.rpc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.commons.lang3.StringUtils;
import pers.liuqi.rpc.config.Constant;
import pers.liuqi.rpc.config.ServiceInfoConfig;
import pers.liuqi.rpc.exception.RpcServiceAlreadyStartException;
import pers.liuqi.rpc.net.MessageCodec;
import pers.liuqi.rpc.net.ServiceProviderLoginHandler;
import pers.liuqi.rpc.service.ServiceInfo;
import pers.liuqi.rpc.util.DynamicClassUtil;
import pers.liuqi.rpc.util.LauncherProperties;
import pers.liuqi.rpc.util.RuntimeLogger;

import java.util.Arrays;
import java.util.Map;

/**
 * 管理RPC服务的启动、停止
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public class RpcServiceStarter {

    private static final RpcServiceStarter SINGLETON = new RpcServiceStarter();

    private ServiceInfoConfig config;
    private ServiceInfoManager serviceInfoManager;

    private ServiceProcessor serviceProcessor = new ServiceProcessor();
    private Channel serverChannel;

    private String gameId;
    private String serverId;
    private String ip;
    private int port;

    private RpcServiceStarter() {
    }

    static RpcServiceStarter getSingleton() {
        return SINGLETON;
    }

    /**
     * 启动本地服务
     *
     * @param gameId   服务器所属大区
     * @param serverId 服务器ID，即subGame
     * @param ip       本地IP
     * @param port     本地端口
     */
    public static void start(String gameId, String serverId, String ip, int port) throws Exception {
        SINGLETON.start0(LauncherProperties.serverConfigPath + "service.xml", gameId, serverId, ip, port);
    }

    /**
     * 测试接口
     *
     * @param configFile
     * @param gameId
     * @param serverId
     * @param ip
     * @param port
     * @throws Exception
     */
    public static void start(String configFile, String gameId, String serverId, String ip, int port) throws Exception {
        SINGLETON.start0(configFile, gameId, serverId, ip, port);
    }

    /**
     * 停止服务
     */
    public static void shutdown() {
        SINGLETON.shutdown0();
    }

    private synchronized void start0(String configFile, String gameId, String serverId, String ip, int port) throws Exception {
        if (this.config != null) {
            throw new RpcServiceAlreadyStartException();
        }

        //设置启动参数、加载配置文件
        this.gameId = gameId;
        this.serverId = serverId;
        this.ip = ip;
        this.port = port;
        this.config = new ServiceInfoConfig(configFile, this::rebuildLocalService);
        this.config.load();

        //启动服务治理功能呢
        this.serviceInfoManager = new ServiceInfoManager();
        this.serviceInfoManager.start(this.config.getZkAddress());

        //启动本地服务
        this.rebuildLocalService();

        //启动服务线程
        this.serviceProcessor.start();

        //监听服务端口
        startNetty();
    }

    /**
     * 开始监听服务端口
     */
    private void startNetty() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        ChannelPipeline pipeline = channel.pipeline();

                        //基于长度的拆包、封包
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, Constant.PACKAGE_LENGTH, 0, Constant.PACKAGE_LENGTH));
                        pipeline.addLast(new LengthFieldPrepender(Constant.PACKAGE_LENGTH));

                        //字符串的编解码
                        pipeline.addLast(new StringDecoder());
                        pipeline.addLast(new StringEncoder());

                        //对象的序列化反序列化
                        pipeline.addLast(new MessageCodec());
                        pipeline.addLast(new ServiceProviderLoginHandler());
                    }
                });

        ChannelFuture future = serverBootstrap.bind(port).sync();
        serverChannel = future.channel();
    }

    private synchronized void shutdown0() {
        //停止接受网络连接
        serverChannel.close();

        //停止远程服务接口
        for (Map<String, Object> serviceMap : RpcContext.getServiceProxyMap().values()) {
            for (Object serviceCaller : serviceMap.values()) {
                if (serviceCaller instanceof AbstractServiceRemoteProxy) {
                    ((AbstractServiceRemoteProxy) serviceCaller).destroy();
                }
            }
        }

        //停止本地服务、从注册中心删除相关数据
        RpcContext.getServiceProviderMap().values().forEach(AbstractServiceProvider::destroy);
        RpcContext.getServiceProviderMap().keySet().forEach(serverName -> serviceInfoManager.unRegisterService(serverName, serverId));
        RpcContext.shutdown();

        //关闭与注册中心的连接
        this.serviceInfoManager.shutdown();

        //停止服务线程
        this.serviceProcessor.shutdown();
    }

    /**
     * 本地服务配置变更时，重建本地服务
     */
    private void rebuildLocalService() {
        Map<String, AbstractServiceProvider> localServiceMap = RpcContext.getServiceProviderMap();
        Map<String, String> serviceMap = config.getServiceMap();
        for (Map.Entry<String, String> entry : serviceMap.entrySet()) {
            String serviceName = entry.getKey();
            String serviceInterface = entry.getValue();

            try {
                //未找到任何接口类
                Class<?> interfaceClass = Class.forName(serviceInterface);
                if (!needBuildInvoker(localServiceMap, serviceName, interfaceClass)) {
                    continue;
                }

                //启动服务
                AbstractServiceProvider invoker = DynamicClassUtil.buildServiceProvider(interfaceClass);
                invoker.getService().start();
                localServiceMap.put(serviceName, invoker);
                //并通知注册中心
                serviceInfoManager.registerService(buildLocalInfo(serviceName, serviceInterface));
            } catch (Exception e) {
                RuntimeLogger.error((String.format("build service invoker service(%s) interfaceClass(%s) error", serviceName, serviceInterface)), e);
            }
        }

        //销毁服务
        localServiceMap.entrySet().removeIf(entry -> {
            try {
                if (!serviceMap.containsKey(entry.getKey())) {
                    //销毁本地服务
                    entry.getValue().destroy();
                    //并通知注册中心
                    serviceInfoManager.unRegisterService(entry.getKey(), getLocalServerId());
                    return true;
                }
                return false;
            } catch (Exception e) {
                RuntimeLogger.error(String.format("local service(%s) destroy fail", entry.getKey()), e);
                return false;
            }
        });
    }

    private boolean needBuildInvoker(Map<String, AbstractServiceProvider> localServiceMap, String serviceName, Class<?> interfaceClass) {
        AbstractServiceProvider invoker = localServiceMap.get(serviceName);
        if (invoker == null) {
            //当前还没有该服务
            return true;
        } else if (Arrays.stream(invoker.getService().getClass().getInterfaces()).noneMatch(i -> i == interfaceClass)) {
            //存在的同名服务和配置的服务接口不匹配
            invoker.destroy();
            localServiceMap.remove(serviceName);
            return true;
        }
        return false;
    }

    private ServiceInfo buildLocalInfo(String serviceName, String serviceInterface) {
        ServiceInfo info = new ServiceInfo();
        info.setId(this.serverId);
        info.setIp(this.ip);
        info.setPort(this.port);
        info.setName(serviceName);
        info.setServiceInterfaceName(serviceInterface);
        info.setAvailable(true);
        return info;
    }

    boolean isLocalServer(String serverId) {
        return StringUtils.equals(serverId, getLocalServerId());
    }

    String getLocalServerId() {
        return serverId;
    }
}
