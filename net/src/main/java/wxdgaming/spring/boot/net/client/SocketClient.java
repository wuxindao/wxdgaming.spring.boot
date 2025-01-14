package wxdgaming.spring.boot.net.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import wxdgaming.spring.boot.core.InitPrint;
import wxdgaming.spring.boot.core.SpringReflect;
import wxdgaming.spring.boot.core.ann.AppStart;
import wxdgaming.spring.boot.core.ann.ReLoad;
import wxdgaming.spring.boot.core.threading.BaseScheduledExecutor;
import wxdgaming.spring.boot.core.threading.Event;
import wxdgaming.spring.boot.core.timer.MyClock;
import wxdgaming.spring.boot.net.*;
import wxdgaming.spring.boot.net.pojo.inner.InnerMessage;
import wxdgaming.spring.boot.net.ssl.WxdSslHandler;

import javax.net.ssl.SSLEngine;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * tcp client
 *
 * @author: wxd-gaming(無心道, 15388152619)
 * @version: 2024-08-15 13:12
 **/
@Slf4j
@Getter
@Accessors(chain = true)
public abstract class SocketClient implements InitPrint, Closeable, ISession {

    protected Bootstrap bootstrap;

    protected final BaseScheduledExecutor executor;
    protected final BootstrapBuilder bootstrapBuilder;
    protected final SocketClientDeviceHandler socketClientDeviceHandler;
    protected final ClientMessageDecode clientMessageDecode;
    protected final ClientMessageEncode clientMessageEncode;

    protected final ClientConfig config;
    /** 所有的连接 */
    protected final SessionGroup sessionGroup = new SessionGroup();
    protected volatile boolean started = false;
    protected volatile boolean closed = false;

    public SocketClient(BaseScheduledExecutor executor,
                        BootstrapBuilder bootstrapBuilder,
                        ClientConfig config,
                        ClientMessageDecode clientMessageDecode,
                        ClientMessageEncode clientMessageEncode) {
        this.executor = executor;
        this.bootstrapBuilder = bootstrapBuilder;
        this.config = config;
        this.socketClientDeviceHandler = new SocketClientDeviceHandler(bootstrapBuilder);
        this.clientMessageDecode = clientMessageDecode;
        this.clientMessageEncode = clientMessageEncode;

        this.executor.scheduleAtFixedRate(
                new Event() {
                    @Override protected void onEvent() throws Throwable {
                        InnerMessage.ReqHeart reqHeart = new InnerMessage.ReqHeart().setMilli(MyClock.millis());
                        writeAndFlush(reqHeart);
                    }
                },
                5, 5, TimeUnit.SECONDS
        );

    }

    public void init() {
        bootstrap = new Bootstrap();
        bootstrap.group(bootstrapBuilder.getClientLoop())
                .channel(bootstrapBuilder.getClient_Socket_Channel_Class())
                /*链接超时设置*/
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.config.getConnectTimeout())
                /*是否启用心跳保活机机制*/
                .option(ChannelOption.SO_KEEPALIVE, true)
                /*发送缓冲区 影响 channel.isWritable()*/
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1, 64 * 1024 * 1024))
                /*使用内存池*/
                .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(64, 1024, 64 * 1024 * 1024))
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        if (bootstrapBuilder.isDebugLogger()) {
                            pipeline.addLast(new LoggingHandler("DEBUG"));/*设置log监听器，并且日志级别为debug，方便观察运行流程*/
                        }
                        if (config.isEnableSsl()) {
                            SSLEngine sslEngine = config.getSslContext().createSSLEngine();
                            sslEngine.setUseClientMode(true);
                            sslEngine.setNeedClientAuth(false);
                            pipeline.addFirst("sslhandler", new WxdSslHandler(sslEngine));
                        }
                        /*空闲链接检查*/
                        int idleTime = config.getIdleTimeout();
                        if (idleTime > 0) {
                            pipeline.addLast(new IdleStateHandler(0, 0, idleTime, TimeUnit.SECONDS));
                        }
                        /*处理链接*/
                        pipeline.addLast("device-handler", socketClientDeviceHandler);
                        /*解码消息*/
                        pipeline.addLast("decode", clientMessageDecode);
                        /*解码消息*/
                        pipeline.addLast("encode", clientMessageEncode);

                        addChanelHandler(socketChannel, pipeline);
                    }
                });
    }

    protected void addChanelHandler(SocketChannel socketChannel, ChannelPipeline pipeline) {}

    @AppStart
    @Order(2000)
    public synchronized void start() throws InterruptedException {
        if (started) return;
        started = true;
        connect();
    }

    @AppStart
    @ReLoad
    public void scanHandlers(SpringReflect springReflect) {
        getClientMessageDecode().getDispatcher().scanHandlers(springReflect.content(), new String[]{NetScan.class.getPackageName()});
        getClientMessageDecode().getDispatcher().scanHandlers(springReflect.content(), config.getScanHandlers());
    }

    @AppStart
    @ReLoad
    public void scanMessages() {
        getClientMessageDecode().getDispatcher().scanMessages(Thread.currentThread().getContextClassLoader(), new String[]{NetScan.class.getPackageName()});
        getClientMessageDecode().getDispatcher().scanMessages(Thread.currentThread().getContextClassLoader(), config.getScanMessages());
    }

    @Override public void close() {
        closed = true;
        sessionGroup.duplicate().forEach(session -> session.close("shutdown"));
        log.info("shutdown tcp client：{}:{}", config.getHost(), config.getPort());
    }

    public final void connect() {
        if (sessionGroup.size() >= config.getMaxConnectNum()) {
            log.error("{} 连接数已经达到最大连接数：{}", this.getClass().getSimpleName(), config.getMaxConnectNum());
            return;
        }
        connect(null);
    }

    public ChannelFuture connect(Consumer<SocketSession> consumer) {
        return bootstrap.connect(config.getHost(), config.getPort())
                .addListener((ChannelFutureListener) future -> {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        log.error("{} connect error {}", this.getClass().getSimpleName(), cause.toString());
                        if (reconnection()) {
                            log.info("{} reconnection", this.getClass().getSimpleName());
                        }
                        return;
                    }
                    Channel channel = future.channel();
                    SocketSession socketSession = new SocketSession(SocketSession.Type.client, channel, false);
                    socketSession.setMaxFrameBytes(getConfig().getMaxFrameBytes());
                    socketSession.setMaxFrameLength(getConfig().getMaxFrameLength());
                    socketSession.setSsl(config.isEnableSsl());
                    log.debug("{} connect success {}", this.getClass().getSimpleName(), channel);

                    sessionGroup.add(socketSession);
                    /*添加事件，如果链接关闭了触发*/
                    socketSession.getChannel().closeFuture().addListener(closeFuture -> {
                        sessionGroup.remove(socketSession);
                        reconnection();
                    });

                    socketClientDeviceHandler.getSessionHandler().openSession(socketSession);

                    if (consumer != null) {
                        consumer.accept(socketSession);
                    }
                });
    }

    AtomicLong atomicLong = new AtomicLong();

    protected boolean reconnection() {

        if (closed
            || !config.isEnableReconnection()
            || executor.isShutdown()
            || executor.isTerminated()
            || executor.isTerminating())
            return false;

        long l = atomicLong.get();
        if (l < 10) {
            l = atomicLong.incrementAndGet();
        }

        log.info("{}链接异常 {} 秒 重连", this.hashCode(), l);

        executor.schedule(() -> {connect();}, l, TimeUnit.SECONDS);
        return true;
    }

}
