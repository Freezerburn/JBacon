package testing;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.types.EventStream;
import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 2/5/13
 * Time: 10:33 AM
 */
public class JBaconStreamsTest {
    @Test(timeout = 200)
    public void testLaterWithUtil() {
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() throws Exception {
                return JBacon.later(100, TimeUnit.MILLISECONDS, "lol");
            }
        };
        Utils.expectStreamEvents("testLaterWithUtil",
                f,
                0,
                110,
                TimeUnit.MILLISECONDS,
                "lol");
    }

    @Test(timeout = 300)
    public void testSequentiallyWithUtil() {
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() throws Exception {
                return JBacon.sequentially(100, TimeUnit.MILLISECONDS, "lol", "wut");
            }
        };
        Utils.expectStreamEvents("testSequentiallyWithUtil",
                f,
                0,
                110,
                TimeUnit.MILLISECONDS,
                "lol", "wut");
    }

    @Test(timeout = 450)
    public void testIntervalWithUtil() {
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() throws Exception {
                return JBacon.interval(100, TimeUnit.MILLISECONDS, "lol").take(3);
            }
        };
        Utils.expectStreamEvents("testIntervalWithUtil",
                f,
                0,
                110,
                TimeUnit.MILLISECONDS,
                "lol", "lol", "lol");
    }

    @Test(timeout = 50)
    public void testFromCallbackWithUtil() {
        final F1<F1<String, Void>, String> callback = new F1<F1<String, Void>, String>() {
            @Override
            public String run(F1<String, Void> val) throws Exception {
                val.run("lol");
                return null;
            }
        };
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() throws Exception {
                return JBacon.fromCallback(callback);
            }
        };
        Utils.expectStreamEvents("testFromCallbackWithUtil",
                f,
                "lol");
    }

    @Test(timeout = 100)
    public void testFromArrayWithUtil() {
        final F<EventStream<Float>> f = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() throws Exception {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
            }
        };
        Utils.expectStreamEvents("testFromArrayWithUtil",
                f,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
    }

    @Test(timeout = 100)
    public void testFromArrayTakeWithUtil() {
        final F<EventStream<Float>> f = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() throws Exception {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f).take(4);
            }
        };
        Utils.expectStreamEvents("testFromArrayTakeWithUtil",
                f,
                1.0f, 2.0f, 3.0f, 4.0f);
    }

    @Test(timeout = 100)
    public void testFromArrayFilterWithUtil() {
        final F<EventStream<Float>> f1 = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() throws Exception {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f).filter(false);
            }
        };
        final F<EventStream<Float>> f2 = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() throws Exception {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f).filter(true);
            }
        };
        Utils.expectStreamEvents("testFromArrayFilterWithUtilFalse",
                f1,
                new Float[]{});
        Utils.expectStreamEvents("testFromArrayFilterWithUtilTrue",
                f2,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
    }

    @Test(timeout = 100)
    public void testFromArrayMapWithUtil() {
        final F1<Float, Float> multTwo = new F1<Float, Float>() {
            @Override
            public Float run(Float val) throws Exception {
                System.out.println("multtwo: " + (val * 2.0f));
                return val * 2.0f;
            }
        };
        final F<EventStream<Float>> f = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() throws Exception {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f).map(multTwo);
            }
        };
        Utils.expectStreamEvents("testFromArrayMapWithUtil",
                f,
                2.0f, 4.0f, 6.0f, 8.0f);
    }
}
