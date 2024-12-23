package wxdgaming.spring.boot.rpc;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import wxdgaming.spring.boot.core.InitPrint;
import wxdgaming.spring.boot.core.SpringReflectContent;
import wxdgaming.spring.boot.core.json.FastJsonUtil;
import wxdgaming.spring.boot.core.util.StringsUtil;
import wxdgaming.spring.boot.net.SocketSession;
import wxdgaming.spring.boot.rpc.pojo.RpcMessage;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * rpc 派发
 *
 * @author: wxd-gaming(無心道, 15388152619)
 * @version: 2024-12-20 15:25
 **/
@Slf4j
@Getter
@Setter
public class RpcDispatcher implements InitPrint {

    private String RPC_TOKEN;
    private String[] packages = null;
    private final ConcurrentHashMap<String, RpcActionMapping> rpcHandlerMap = new ConcurrentHashMap<>();
    private final AtomicLong atomicLong = new AtomicLong(0);
    private final ConcurrentSkipListMap<Long, CompletableFuture<String>> rpcEvent = new ConcurrentSkipListMap<>();

    public RpcDispatcher(String RPC_TOKEN) {
        this.RPC_TOKEN = RPC_TOKEN;
    }

    public RpcDispatcher(String RPC_TOKEN, String[] packages) {
        this.RPC_TOKEN = RPC_TOKEN;
        this.packages = packages;
    }

    public void initMapping(SpringReflectContent springReflectContent) {
        initMapping(springReflectContent, packages);
    }

    public void initMapping(SpringReflectContent springReflectContent, String[] params) {
        Predicate<Class<?>> filter = clazz -> {
            if (params == null || params.length == 0) return true;
            for (String p : params) {
                if (clazz.getName().startsWith(p)) return true;
            }
            return false;
        };
        springReflectContent.withMethodAnnotated(RPC.class)
                .filter(t -> filter.test(t.getLeft().getClass()))
                .forEach(t -> {
                    t.getRight().setAccessible(true);
                    String value = "";
                    RequestMapping requestMapping = t.getLeft().getClass().getAnnotation(RequestMapping.class);
                    if (requestMapping != null && requestMapping.value().length > 0) {
                        value = requestMapping.value()[0];
                    }
                    RPC annotation = t.getRight().getAnnotation(RPC.class);
                    String mapping = annotation.value();
                    if (StringsUtil.emptyOrNull(mapping)) {
                        value += "/" + t.getRight().getName();
                    } else {
                        value += mapping;
                    }
                    RpcActionMapping oldMapping = rpcHandlerMap.put(value, new RpcActionMapping(annotation, t.getLeft(), t.getRight()));
                    if (oldMapping != null) {
                        if (!oldMapping.getBean().getClass().getName().endsWith(t.getLeft().getClass().getName())) {
                            throw new RuntimeException("RPC 处理器重复：" + oldMapping.getBean().getClass().getName() + " - " + t.getLeft().getClass().getName());
                        }
                    }
                    log.debug("rpc register path={}, {}#{}", value, t.getLeft().getClass().getName(), t.getRight().getName());
                });
    }

    public Object rpcReqSocketAction(SocketSession session, long rpcId, long targetId, String path, String remoteParams) throws Exception {
        RpcActionMapping rpcActionMapping = getRpcHandlerMap().get(path);
        if (rpcActionMapping == null) {
            log.error("rpc 调用异常 rpcId={}, path={}, params={}", rpcId, path, remoteParams);
            response(session, rpcId, targetId, 9, "Not found path=【" + path + "】!");
            return null;
        }
        Parameter[] parameters = rpcActionMapping.getMethod().getParameters();
        Object[] params = new Object[parameters.length];
        for (int i = 0; i < params.length; i++) {
            Parameter parameter = parameters[i];
            Type type = parameter.getParameterizedType();
            if (type instanceof Class<?> clazz) {
                if (clazz.isAssignableFrom(session.getClass())) {
                    params[i] = session;
                } else {
                    /*实现注入*/
                    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                    if (requestParam != null) {
                        String name = requestParam.name();
                        if (StringsUtil.emptyOrNull(name)) {
                            name = requestParam.value();
                        }
                        if (StringsUtil.emptyOrNull(name)) {
                            throw new RuntimeException(rpcActionMapping.getBean().getClass().getName() + "#" + rpcActionMapping.getMethod().getName() + ", 无法识别 " + (i + 1) + " 参数 RequestParam 指定 name " + clazz);
                        }
                        params[i] = FastJsonUtil.parse(remoteParams).getObject(name, clazz);
                        continue;
                    }
                    if (clazz.isAssignableFrom(String.class)) {
                        params[i] = remoteParams;
                    } else if (clazz.isAssignableFrom(JSONObject.class)) {
                        params[i] = FastJsonUtil.parse(remoteParams);
                    } else {
                        throw new RuntimeException(rpcActionMapping.getBean().getClass().getName() + "#" + rpcActionMapping.getMethod().getName() + ", 无法识别 " + (i + 1) + " 参数 " + clazz);
                    }
                }
            }
        }

        /* 调用 */
        return rpcActionMapping.getMethod().invoke(rpcActionMapping.getBean(), params);
    }


    public boolean checkRpcToken(SocketSession session) {
        return Boolean.TRUE.equals(session.attribute("checkRpcToken"));
    }

    /**
     * 请求 rpc 执行
     *
     * @param session 链接
     * @param path    路径
     * @param params  参数
     * @return
     * @author: wxd-gaming(無心道, 15388152619)
     * @version: 2024-08-22 19:41
     */
    public Mono<String> request(SocketSession session, long targetId, String path, JSONObject params) {
        return request(session, targetId, path, FastJsonUtil.toJsonWriteType(params));
    }

    /**
     * 请求 rpc 执行
     *
     * @param session 链接
     * @param path    路径
     * @param params  参数
     * @return
     * @author: wxd-gaming(無心道, 15388152619)
     * @version: 2024-08-22 19:41
     */
    public Mono<String> request(SocketSession session, long targetId, String path, String params) {
        if (!checkRpcToken(session)) {
            throw new RuntimeException("rpcToken 验证失败");
        }
        long rpcId = atomicLong.incrementAndGet();
        RpcMessage.ReqRPC rpcMessage = new RpcMessage.ReqRPC()
                .setRpcId(rpcId)
                .setTargetId(targetId)
                .setPath(path)
                .setParams(params);
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        rpcEvent.put(rpcId, completableFuture);
        session.writeAndFlush(rpcMessage);
        return Mono.fromFuture(completableFuture);
    }

    /**
     * 回应 rpc 执行结果
     *
     * @param session 链接
     * @param rpcId   请求 id
     * @param code    执行状态
     * @param params  参数
     * @author: wxd-gaming(無心道, 15388152619)
     * @version: 2024-08-22 19:41
     */
    public void response(SocketSession session, long rpcId, long targetId, int code, String params) {
        RpcMessage.ResRPC resRPC = new RpcMessage.ResRPC()
                .setRpcId(rpcId)
                .setTargetId(targetId)
                .setCode(code)
                .setParams(params);
        session.writeAndFlush(resRPC);
    }

}
