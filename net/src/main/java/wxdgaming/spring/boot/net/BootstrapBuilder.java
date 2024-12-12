package wxdgaming.spring.boot.net;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import wxdgaming.spring.boot.core.InitPrint;
import wxdgaming.spring.boot.core.threading.WxdThreadFactory;
import wxdgaming.spring.boot.core.util.StringsUtil;
import wxdgaming.spring.boot.net.server.ServerConfig;
import wxdgaming.spring.boot.net.server.ServerMessageDecode;
import wxdgaming.spring.boot.net.server.ServerMessageEncode;
import wxdgaming.spring.boot.net.server.SocketService;

import java.lang.reflect.Constructor;

/**
 * 配置项
 *
 * @author: wxd-gaming(無心道, 15388152619)
 * @version: 2024-08-14 20:33
 **/
@Slf4j
@Getter
@Setter
@Accessors(chain = true)
@Configuration
@ConfigurationProperties("socket")
public class BootstrapBuilder implements InitPrint {

    private boolean debugLogger = false;
    /** 输出一些基本日志 */
    private boolean printLogger = false;
    /** netty boss 线程 多个服务共享 */
    private int bossThreadSize = 3;
    /** netty work 线程 多个服务共享 */
    private int workerThreadSize = 20;

    private EventLoopGroup bossLoop;
    private EventLoopGroup workerLoop;
    /** 服务监听的channel */
    private Class<? extends ServerChannel> Server_Socket_Channel_Class;

    @PostConstruct
    public void init() {
        bossLoop = createGroup(getBossThreadSize(), "boss");
        workerLoop = createGroup(getWorkerThreadSize(), "worker");
        if (Epoll.isAvailable()) {
            Server_Socket_Channel_Class = EpollServerSocketChannel.class;
        } else {
            Server_Socket_Channel_Class = NioServerSocketChannel.class;
        }
    }

    public static EventLoopGroup createGroup(int size, String prefix) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(size, new WxdThreadFactory(prefix));
        } else {
            return new NioEventLoopGroup(size, new WxdThreadFactory(prefix));
        }
    }

    public static SocketService createSocketService(BootstrapBuilder bootstrapBuilder,
                                                    ServerMessageDecode serverMessageDecode,
                                                    ServerMessageEncode serverMessageEncode, ServerConfig cfg) throws Exception {

        if (StringsUtil.emptyOrNull(cfg.getServiceClass())) {
            cfg.setServiceClass(SocketService.class.getName());
        }

        Class aClass = Thread.currentThread().getContextClassLoader().loadClass(cfg.getServiceClass());
        Constructor<SocketService> declaredConstructor = aClass.getDeclaredConstructors()[0];
        return declaredConstructor.newInstance(
                bootstrapBuilder,
                cfg,
                serverMessageDecode,
                serverMessageEncode
        );
    }

}
