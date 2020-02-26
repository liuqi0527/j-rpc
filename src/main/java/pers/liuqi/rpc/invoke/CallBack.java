package pers.liuqi.rpc.invoke;

/**
 * 异步服务调用的回调接口
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public interface CallBack<T> {

    /**
     * 异步调用结束后，会执行此方法
     *
     * @param t 异步调用返回的调用结果
     */
    void call(T t);

}
