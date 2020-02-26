package pers.liuqi.rpc.net;

import pers.liuqi.rpc.AbstractServiceProvider;
import pers.liuqi.rpc.RpcContext;
import pers.liuqi.rpc.config.Constant;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

/**
 * 用于服务提供者接收到网络连接收，将其指定给具体的服务Handler处理
 *
 * @author LiuQi - [Created on 2018-08-20]
 */
public class ServiceProviderLoginHandler extends SimpleChannelInboundHandler<Object> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        AttributeKey<Object> key = AttributeKey.valueOf(Constant.KEY_SERVICE_NAME);

        if (ctx.channel().hasAttr(key)) {
            //已登陆的连接，直接进行后续处理
            ctx.fireChannelRead(msg);
        } else if (!tryLogin(ctx, msg)) {
            //未登录连接，第一个消息需要时登陆消息，否则断开连接（后续可以增加等待时间）
            ctx.channel().close();
        }
    }

    private boolean tryLogin(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof LoginMessage)) {
            return false;
        }

        String serviceName = ((LoginMessage) message).getServiceName();
        AbstractServiceProvider provider = RpcContext.getProvider(serviceName);
        if (provider == null) {
            return false;
        }

        //根据客户端发过来的服务名，将对应的ServiceProvider注册到channel的管道中
        AttributeKey<Object> key = AttributeKey.valueOf(Constant.KEY_SERVICE_NAME);
        ctx.channel().attr(key).set(serviceName);
        ctx.channel().pipeline().addLast(provider);
        return true;
    }

}
