package pers.liuqi.rpc.invoke;

import java.util.Arrays;

/**
 * 封装服务调用相关参数
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
public class Invoke {

    private long id;
    private String methodName;
    private Object[] params;

    private Invoke() {
    }

    public Invoke(long id, String methodName, Object[] params) {
        this.id = id;
        this.methodName = methodName;
        this.params = params;
    }

    public long getId() {
        return id;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "Invoke{" +
                "id=" + id +
                ", methodName='" + methodName + '\'' +
                ", params=" + Arrays.toString(params) +
                '}';
    }
}
