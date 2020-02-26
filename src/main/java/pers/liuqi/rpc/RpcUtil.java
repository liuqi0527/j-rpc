package pers.liuqi.rpc;

import com.egls.server.utils.random.RandomUtil;
import pers.liuqi.rpc.exception.ServiceInvalidException;
import pers.liuqi.rpc.invoke.CallBack;
import pers.liuqi.rpc.invoke.Future;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RPC服务使用者需要用到的接口
 *
 * @author LiuQi - [Created on 2018-08-08]
 */
public class RpcUtil {

    /**
     * 获取指定服务接口，指定了具体的服务提供者
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> clazz, String serverId) {
        Map<String, Object> serviceMap = RpcContext.getServiceProxyMap().get(clazz);
        if (serviceMap == null || !serviceMap.containsKey(serverId)) {
            throw new ServiceInvalidException(clazz);
        }
        return (T) serviceMap.get(serverId);
    }

    /**
     * 获取指定服务接口，有多个提供者的情况下，随机选择一个
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> clazz) {
        Map<String, Object> serviceMap = RpcContext.getServiceProxyMap().get(clazz);
        if (serviceMap == null || serviceMap.isEmpty()) {
            throw new ServiceInvalidException(clazz);
        }

        //如果本地有提供该服务，则返回本地服务
        Object localService = serviceMap.get(RpcServiceStarter.getSingleton().getLocalServerId());
        if (localService != null) {
            return (T) localService;
        }

        //过滤掉不可用的服务网
        List<AbstractServiceRemoteProxy> availableList = serviceMap.values().stream()
                .filter(service -> service instanceof AbstractServiceRemoteProxy)
                .map(service -> ((AbstractServiceRemoteProxy) service))
                .filter(AbstractServiceRemoteProxy::isValid)
                .collect(Collectors.toList());
        if (availableList.isEmpty()) {
            throw new ServiceInvalidException(clazz);
        }

        //随机取一个
        int index = RandomUtil.randomInt(availableList.size());
        return ((T) availableList.stream().skip(index).findAny().orElse(null));
    }

    /**
     * 异步调用
     */
    public static <T> Future<T> async(T t, CallBack<T> callBack) {
        return RpcContext.async(t, callBack);
    }
}
