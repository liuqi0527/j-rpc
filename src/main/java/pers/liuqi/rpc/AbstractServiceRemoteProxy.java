package pers.liuqi.rpc;

import com.egls.server.utils.function.Ticker;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import pers.liuqi.rpc.config.Constant;
import pers.liuqi.rpc.invoke.Invoke;
import pers.liuqi.rpc.invoke.InvokeResult;
import pers.liuqi.rpc.net.LoginMessage;
import pers.liuqi.rpc.net.MessageCodec;
import pers.liuqi.rpc.service.ServiceInfo;
import pers.liuqi.rpc.util.RuntimeLogger;

/**
 * 远程服务在本地的代理
 * 所有的远程调用都通过此接口发送到远程机器上，并接受处理结果
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
@ChannelHandler.Sharable
public abstract class AbstractServiceRemoteProxy extends SimpleChannelInboundHandler<InvokeResult> implements Ticker {

    private ServiceInfo info;

    private Channel channel;

    private long lastTouchMillis;

    public AbstractServiceRemoteProxy(ServiceInfo info) {
        this.info = info;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, InvokeResult result) throws Exception {
        RpcContext.onInvokeFinish(result);
    }

    /**
     * 处理网络指令
     */
    @Override
    public void tick() {
        //断线重连
        if (isNetInvalid() && lastTouchMillis + Constant.RE_CONNECT_INTERVAL < System.currentTimeMillis()) {
            lastTouchMillis = System.currentTimeMillis();

            EventLoopGroup bossGroup = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap
                    .group(bossGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            ChannelPipeline pipeline = channel.pipeline();

                            //基于长度的拆包、封包
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, Constant.PACKAGE_LENGTH, 0, Constant.PACKAGE_LENGTH));
                            pipeline.addLast(new LengthFieldPrepender(Constant.PACKAGE_LENGTH));

                            //字符串的编解码
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());

                            //对象的序列化反序列化
                            pipeline.addLast(new MessageCodec());
                            pipeline.addLast(AbstractServiceRemoteProxy.this);
                        }
                    });

            try {
                ChannelFuture future = bootstrap.connect(info.getIp(), info.getPort()).sync();
                this.channel = future.channel();
                this.channel.writeAndFlush(new LoginMessage(info.getName()));
            } catch (InterruptedException e) {
                RuntimeLogger.error(e);
            }
        }
    }

    /**
     * 子类将方法名字、参数列表传入，由本方法将调用发送给远程服务提供者
     */
    protected void remoteInvoke(String method, Object[] methodParams) {
        //封装调用参数
        Invoke invoke = RpcContext.buildInvoke(info.getName(), method, methodParams);

        //发送到网络
        if (channel != null) {
            channel.writeAndFlush(invoke);
        }
    }

    public boolean isValid() {
        return !isNetInvalid();
    }

    private boolean isNetInvalid() {
        return channel == null || !channel.isActive();
    }

    /**
     * 销毁远程服务接口
     */
    void destroy() {
        if (!isNetInvalid()) {
            channel.close();
        }
    }


}
