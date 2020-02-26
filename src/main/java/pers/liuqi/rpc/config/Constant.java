package pers.liuqi.rpc.config;

/**
 * @author LiuQi - [Created on 2018-08-03]
 */
public class Constant {
    //@formatter:off


    /**
     * freemarker 模板
     */
    public static final String TEMPLATE_INVOKER         = "provider.ftl";
    public static final String TEMPLATE_LOCAL_CALLER    = "localProxy.ftl";
    public static final String TEMPLATE_REMOTE_CALLER   = "remoteProxy.ftl";


    public static final long RE_CONNECT_INTERVAL = 5000L;

    /**
     * 生成的代理类所在的包
     */
    public static final String DYNAMIC_CLASS_PACKAGE = "pers.liuqi.rpc";

    /**
     * 服务数据在zookeeper上的根节点
     */
    public static final String ZOOKEEPER_ROOT_PATH  = "/rpc";

    /**
     * 附加在Channel上的属性key，用于表示该连接已经登录过，并注册到对应的提供者处
     */
    public static final String KEY_SERVICE_NAME = "service_name";

    /**
     * 网络数据包中的包头长度，表示整个数据包后续的长度
     */
    public static final int PACKAGE_LENGTH = 4;

    /**
     * 异步回调的超时时间，超过此时间还未受到结果，则丢弃该回调
     */
    public static final long CALL_BACK_EXPIRE = 5000;


    //@formatter:on
}
