package wxdgaming.spring.boot.core.lang;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import wxdgaming.spring.boot.core.json.FastJsonUtil;

import java.io.Serializable;

/**
 * @author: wxd-gaming(無心道, 15388152619)
 * @version: 2020-11-18 13:46
 **/
@Getter
@Setter
@Accessors(chain = true)
public class TupleInt extends ObjectBase implements Serializable {

    public void t0(String[] args) {
        TupleInt tuple2 = new TupleInt(1, 1);
        String s = FastJsonUtil.toJsonFmt(tuple2);
        System.out.println(s);
        TupleInt object = FastJsonUtil.parse(s, TupleInt.class);
        System.out.println(object);
    }

    protected int left;
    protected int right;

    public TupleInt() {
    }

    public TupleInt(int left, int right) {
        this.left = left;
        this.right = right;
    }
}
