package pers.liuqi.rpc.service;

import java.io.Serializable;

/**
 * @author LiuQi - [Created on 2018-08-02]
 */
public class ServiceInfo implements Serializable {

    private String id;
    private String name;
    private String ip;
    private int port;
    private String serviceInterfaceName;
    private boolean available;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceInterfaceName() {
        return serviceInterfaceName;
    }

    public void setServiceInterfaceName(String serviceInterfaceName) {
        this.serviceInterfaceName = serviceInterfaceName;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", serviceInterfaceName='" + serviceInterfaceName + '\'' +
                ", available=" + available +
                "}\n";
    }
}
