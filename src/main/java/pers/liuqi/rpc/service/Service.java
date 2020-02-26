package pers.liuqi.rpc.service;

/**
 * 服务实现类需要实现的接口
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public interface Service {

    /**
     * 服务启动时的初始化操作
     */
    void start();

    /**
     * 服务器停止时执行的清理、存储操作
     */
    void shutdown();

    /**
     * 具体业务逻辑的周期任务执行结果，默认没有周期任务，可以不用实现此方法
     *
     * @return 周期任务的间隔，单位毫秒
     */
    default long getTickInterval() {
        return -1;
    }

    /**
     * 服务实现类需要周期执行的任务，默认没有周期任务，可以不用实现此方法
     */
    default void tick() {

    }
}
