package pers.liuqi.rpc.invoke;

/**
 * 封装服务调用结果
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public class InvokeResult<T> {

    private long invokeId;
    private T result;

    private InvokeResult() {
    }

    public InvokeResult(long invokeId, T result) {
        this.invokeId = invokeId;
        this.result = result;
    }

    public long getInvokeId() {
        return invokeId;
    }

    public T getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "InvokeResult{" +
                "invokeId=" + invokeId +
                ", result=" + result +
                '}';
    }
}
