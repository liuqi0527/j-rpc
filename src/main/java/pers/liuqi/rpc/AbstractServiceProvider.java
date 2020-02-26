package pers.liuqi.rpc;

import com.egls.server.utils.function.Ticker;
import groovy.util.logging.Slf4j;
import io.netty.channel.*;
import pers.liuqi.rpc.invoke.Invoke;
import pers.liuqi.rpc.invoke.InvokeResult;
import pers.liuqi.rpc.service.Service;
import pers.liuqi.rpc.util.Recorder;
import pers.liuqi.rpc.util.RuntimeLogger;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * <pre>
 * 服务提供者
 * 此管理类持有具体服务实现的实例，并管理服务实现类的启动、停止以及周期任务
 * 同时接受并处理所有的远程调用、本地调用，并将执行结果原路发送回去
 * </pre>
 *
 * @author LiuQi - [Created on 2018-08-02]
 */
@Slf4j
@ChannelHandler.Sharable
public abstract class AbstractServiceProvider extends SimpleChannelInboundHandler<Invoke> implements Ticker {

    private long serviceTickInterval;
    private long nextTickTime;

    private Map<Channel, Queue<Invoke>> remoteInvokeMap = new ConcurrentHashMap<>();
    private Queue<Invoke> localInvokeQueue = new ConcurrentLinkedQueue<>();

    public AbstractServiceProvider(long serviceTickInterval) {
        this.serviceTickInterval = serviceTickInterval;
    }

    /**
     * 本地调用
     */
    public void localInvoke(Invoke invoke) {
        localInvokeQueue.add(invoke);
    }

    /**
     * 远程调用
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Invoke msg) throws Exception {
        remoteInvokeMap.computeIfAbsent(ctx.channel(), key -> new ConcurrentLinkedQueue<>()).add(msg);
    }

    /**
     * 处理远程调用、本地调用
     */
    @Override
    public final void tick() {
        //处理本地调用
        loopInvoke(localInvokeQueue, RpcContext::onInvokeFinish);

        //处理远程调用
        remoteInvokeMap.forEach((channel, queue) -> {
            loopInvoke(queue, result -> {
                if (channel.isActive()) {
                    channel.write(result);
                }
            });
            channel.flush();
        });

        //删除不活跃的链接记录
        remoteInvokeMap.keySet().removeIf(channel -> !channel.isActive());

        //执行服务实现的周期任务
        if (serviceTickInterval > 0 && nextTickTime >= System.currentTimeMillis()) {
            nextTickTime += System.currentTimeMillis() + serviceTickInterval;
            getService().tick();
        }
    }

    private void loopInvoke(Queue<Invoke> queue, Consumer<InvokeResult<Object>> resultConsumer) {
        Invoke invoke;
        int size = queue.size();
        while (size-- > 0 && (invoke = queue.poll()) != null) {
            try {
                //执行服务方法
                long start = System.nanoTime();
                Object result = invoke(invoke);
                Recorder.record(getService().getClass().getSimpleName(), invoke.getMethodName(), System.nanoTime() - start);

                //封装返回值
                InvokeResult<Object> invokeResult = new InvokeResult<>(invoke.getId(), result);
                resultConsumer.accept(invokeResult);
            } catch (Exception e) {
                RuntimeLogger.error("execute local invoke error", e);
            }
        }
    }


    /**
     * 销毁服务
     */
    void destroy() {
        remoteInvokeMap.keySet().forEach(ChannelOutboundInvoker::close);
        remoteInvokeMap.clear();

        getService().shutdown();
    }

    /**
     * 子类实现，路由到具体的执行方法
     *
     * @param invoke
     * @return
     */
    protected abstract Object invoke(Invoke invoke);

    /**
     * 获取服务的实现类
     *
     * @return
     */
    public abstract Service getService();
}
