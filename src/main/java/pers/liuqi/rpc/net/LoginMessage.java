package pers.liuqi.rpc.net;

/**
 * 调用者首次连接上提供者后发送的消息
 * 用于指明该连接所用的服务
 *
 * @author LiuQi - [Created on 2018-08-21]
 */
public class LoginMessage {

    private String serviceName;

    public LoginMessage() {
    }

    public LoginMessage(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
