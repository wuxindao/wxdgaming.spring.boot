package wxdgaming.spring.boot.core.ann;

import java.lang.annotation.*;

/**
 * 重载的时候调用的方法
 *
 * @author: wxd-gaming(無心道, 15388152619)
 * @version: 2024-08-14 20:46
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReLoad {

}
