package pers.liuqi.rpc.exception;

import pers.liuqi.rpc.service.ServiceInfo;

/**
 * @author LiuQi - [Created on 2018-08-08]
 */
public class ServiceInvalidException extends RuntimeException {

    public ServiceInvalidException(ServiceInfo info) {
        super(String.format("name -> (%s), id -> (%s), class -> (%s)", info.getName(), info.getId(), info.getServiceInterfaceName()));
    }

    public ServiceInvalidException(Class<?> serviceInterfaceClass) {
        super(String.format("class -> (%s)", serviceInterfaceClass.getName()));
    }
}
