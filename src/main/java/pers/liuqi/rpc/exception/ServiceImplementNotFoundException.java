package pers.liuqi.rpc.exception;

/**
 * @author LiuQi - [Created on 2018-08-08]
 */
public class ServiceImplementNotFoundException extends RuntimeException {

    public ServiceImplementNotFoundException(Class<?> interfaceClass) {
        super(String.format("cant find any class implement %s", interfaceClass.getName()));
    }
}
