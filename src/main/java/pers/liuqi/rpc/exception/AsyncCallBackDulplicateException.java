package pers.liuqi.rpc.exception;

/**
 * @author LiuQi - [Created on 2018-08-02]
 */
public class AsyncCallBackDulplicateException extends RuntimeException {

    public AsyncCallBackDulplicateException(String message) {
        super(message);
    }
}
