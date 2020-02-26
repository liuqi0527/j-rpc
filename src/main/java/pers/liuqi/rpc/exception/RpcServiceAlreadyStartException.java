package pers.liuqi.rpc.exception;

/**
 * @author LiuQi - [Created on 2018-08-03]
 */
public class RpcServiceAlreadyStartException extends RuntimeException {

    public RpcServiceAlreadyStartException() {
        super("rpc service already start");
    }
}
