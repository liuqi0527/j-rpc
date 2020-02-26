package pers.liuqi.rpc.net;

import com.egls.server.utils.databind.json.JsonObject;
import pers.liuqi.rpc.util.RuntimeLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;
import java.util.Objects;

/**
 * <pre>
 *  编解码器
 *  将Java对象与Json字符串互相转换
 *  与应用层互相交换的是Java对象，应用层可以直接收发普通的POJO对象
 *  与下一层编解码器交换的是String对象，由下一层继续编码解码工作
 * </pre>
 *
 * @author LiuQi - [Created on 2018-08-20]
 */
public class MessageCodec extends MessageToMessageCodec<String, Object> {


    /**
     * 将出站调用结果序列换成字符串
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
        try {
            out.add(JsonObject.serialize(new MessageDataWrapper(msg)));
        } catch (Exception e) {
            RuntimeLogger.info("encode -> " + msg, e);
        }
    }

    /**
     * 将入站的调用消息反序列化成POJO对象
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out) {
        MessageDataWrapper dataWrapper = JsonObject.deserialize(msg, MessageDataWrapper.class);
        out.add(dataWrapper.messageData);
    }

    private static class MessageDataWrapper {

        private Object messageData;

        private MessageDataWrapper() {
        }

        MessageDataWrapper(Object messageData) {
            this.messageData = messageData;
            Objects.requireNonNull(messageData);
        }
    }
}
