package pers.liuqi.rpc;

import pers.liuqi.rpc.exception.AsyncCallBackDulplicateException;
import pers.liuqi.rpc.invoke.CallBack;
import pers.liuqi.rpc.invoke.Future;
import pers.liuqi.rpc.invoke.Invoke;
import pers.liuqi.rpc.invoke.InvokeResult;
import pers.liuqi.rpc.util.Recorder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理RPC服务所有的动态实例
 * 包括本地代理、远程代理、服务提供者，异步回调等
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public class RpcContext {


    /**
     * 本地提供的服务列表
     */
    private static Map<String, AbstractServiceProvider> serviceProviderMap = new ConcurrentHashMap<>();

    /**
     * <服务接口类，<服务ID， 服务代理类>>
     */
    private static Map<Class<?>, Map<String, Object>> serviceProxyMap = new HashMap<>();

    /**
     * 回调列表
     */
    private static Map<Long, Future<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * 调用发生时，生成唯一ID用
     */
    private static AtomicLong idBuilder = new AtomicLong(0);

    /**
     * 当前线程最近一次调用的ID，使用一次后清空
     */
    private static ThreadLocal<Long> localLastId = ThreadLocal.withInitial(() -> -1L);

    static Map<String, AbstractServiceProvider> getServiceProviderMap() {
        return serviceProviderMap;
    }

    static Map<Class<?>, Map<String, Object>> getServiceProxyMap() {
        return serviceProxyMap;
    }

    public static AbstractServiceProvider getProvider(String serviceName) {
        return serviceProviderMap.get(serviceName);
    }

    public static boolean isLocalProvider(String serviceName) {
        return serviceProviderMap.containsKey(serviceName);
    }

    static <T> Future<T> async(T t, CallBack<T> callBack) {
        Long lastId = localLastId.get();
        if (lastId == -1) {
            throw new AsyncCallBackDulplicateException(callBack.getClass().getName());
        }

        Future<T> future = new Future<>(callBack);
        futureMap.put(lastId, future);
        localLastId.set(-1L);
        return future;
    }

    /**
     * 封装服务调用参数
     */
    public static Invoke buildInvoke(String serviceName, String methodName, Object[] methodParams) {
        long invokeId = idBuilder.incrementAndGet();
        localLastId.set(invokeId);

        Recorder.invokeStart(serviceName, methodName, invokeId);
        return new Invoke(invokeId, methodName, methodParams);
    }

    /**
     * 异步调用结束
     */
    @SuppressWarnings("unchecked")
    public static <T> void onInvokeFinish(InvokeResult<T> invokeResult) {
        Future<T> future = (Future<T>) futureMap.remove(invokeResult.getInvokeId());
        if (future != null) {
            future.setResult(invokeResult.getResult());
        }
        Recorder.invokeFinish(invokeResult.getInvokeId());
    }

    static void shutdown() {
        serviceProviderMap.clear();
        serviceProxyMap.clear();
        futureMap.clear();

        idBuilder = null;
        localLastId = null;
    }
}
