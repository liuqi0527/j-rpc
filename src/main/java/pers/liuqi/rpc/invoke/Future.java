package pers.liuqi.rpc.invoke;

import pers.liuqi.rpc.util.RuntimeLogger;
import pers.liuqi.rpc.config.Constant;

/**
 * <pre>
 * 表示带有回调的异步调用
 * 当异步调用结果产生时，调用结果会被存储在此类中
 * 此类的实例，由具体的业务线程持有，并检测异步调用结果，执行异步回调
 * </pre>
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public class Future<T> {

    private final CallBack<T> callBack;
    private volatile T result;
    private boolean done;
    private long expireTime;

    public Future(CallBack<T> callBack) {
        this.callBack = callBack;
        this.expireTime = System.currentTimeMillis() + Constant.CALL_BACK_EXPIRE;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isExpire() {
        return expireTime <= System.currentTimeMillis();
    }

    /**
     * 检查异步调用是否完成，如果完成会将调用结果作为参数执行CallBack方法
     */
    public boolean done() {
        if (isDone()) {
            RuntimeLogger.error("callBack already done ", callBack.getClass().getName());
            return true;
        }

        if (result != null) {
            try {
                callBack.call(result);
            } catch (Exception e) {
                RuntimeLogger.error("process invoke result error", e);
            }
            this.done = true;
        }

        return isDone();
    }
}
