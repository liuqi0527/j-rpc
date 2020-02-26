package pers.liuqi.rpc;

import groovy.util.logging.Slf4j;
import pers.liuqi.rpc.util.RuntimeLogger;
import pers.liuqi.rpc.util.RuntimeLogger;

import java.util.Map;

/**
 * 执行服务调用，处理调用结果，以及服务实现的周期任务
 *
 * @author LiuQi - [Created on 2018-08-23]
 */
@Slf4j
class ServiceProcessor extends Thread {

    private volatile boolean shutdown;

    ServiceProcessor() {
        super("Service-Processor");
    }

    synchronized void shutdown() {
        this.shutdown = true;
    }

    @Override
    public void run() {
        while (!shutdown) {
            try {
                RpcContext.getServiceProviderMap().values().forEach(AbstractServiceProvider::tick);

                for (Map<String, Object> serviceMap : RpcContext.getServiceProxyMap().values()) {
                    for (Object serviceCaller : serviceMap.values()) {
                        if (serviceCaller instanceof AbstractServiceRemoteProxy) {
                            ((AbstractServiceRemoteProxy) serviceCaller).tick();
                        }
                    }
                }
            } catch (Exception e) {
                RuntimeLogger.error(e);
            } finally {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    RuntimeLogger.error(e);
                }
            }
        }
    }
}