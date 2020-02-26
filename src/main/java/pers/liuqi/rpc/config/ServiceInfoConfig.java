package pers.liuqi.rpc.config;


import com.egls.server.utils.databind.xml.annotation.XmlElement;
import com.egls.server.utils.databind.xml.annotation.XmlElementDeclineMap;
import com.egls.server.utils.file.loader.BaseFileLoader;
import com.egls.server.utils.file.loader.LoaderManager;
import pers.liuqi.rpc.util.GameXmlObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取本地服务列表的配置
 *
 * @author LiuQi - [Created on 2018-08-03]
 */
public class ServiceInfoConfig extends BaseFileLoader implements GameXmlObject {

    private String configPath;
    private Runnable configUpdateTask;

    @XmlElement("zkAddress")
    private String zkAddress;

    @XmlElementDeclineMap(element = "service", key = "name", value = "interface")
    private LinkedHashMap<String, String> serviceMap;

    public ServiceInfoConfig(String configPath, Runnable configUpdateTask) {
        this.configPath = configPath;
        this.configUpdateTask = configUpdateTask;
    }

    public void load() {
        LoaderManager.loadAndRegisterFile(configPath, this);
    }

    public Map<String, String> getServiceMap() {
        return serviceMap;
    }

    public String getZkAddress() {
        return zkAddress;
    }

    @Override
    public void loadFile(File file) {
        load(file);
        configUpdateTask.run();
    }

}
