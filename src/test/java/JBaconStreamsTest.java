package testing;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.types.EventStream;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

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
    public void testLater() {
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() {
                return JBacon.later(100, TimeUnit.MILLISECONDS, "lol");
            }
        };
        Utils.expectStreamEvents("Test JBacon.later",
                f,
                0,
                110,
                TimeUnit.MILLISECONDS,
                "lol");
    }

    @Test(timeout = 300)
    public void testSequentially() {
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() {
                return JBacon.sequentially(100, TimeUnit.MILLISECONDS, "lol", "wut");
            }
        };
        Utils.expectStreamEvents("Test JBacon.sequentially",
                f,
                0,
                110,
                TimeUnit.MILLISECONDS,
                "lol", "wut");
    }

    @Test(timeout = 450)
    public void testInterval() {
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() {
                return JBacon.interval(100, TimeUnit.MILLISECONDS, "lol").take(3);
            }
        };
        Utils.expectStreamEvents("Test JBacon.interval (take 3)",
                f,
                0,
                110,
                TimeUnit.MILLISECONDS,
                "lol", "lol", "lol");
    }

    @Test(timeout = 50)
    public void testFromCallback() {
        final F1<F1<String, Void>, String> callback = new F1<F1<String, Void>, String>() {
            @Override
            public String run(F1<String, Void> val) {
                val.run("lol");
                return null;
            }
        };
        final F<EventStream<String>> f = new F<EventStream<String>>() {
            @Override
            public EventStream<String> run() {
                return JBacon.fromCallback(callback);
            }
        };
        Utils.expectStreamEvents("Test JBacon.callback",
                f,
                "lol");
    }

    @Test(timeout = 100)
    public void testFromArray() {
        final F<EventStream<Float>> f = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
            }
        };
        Utils.expectStreamEvents("Test JBacon.fromArray",
                f,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
    }

    @Test(timeout = 100)
    public void testFromArrayTake() {
        final F<EventStream<Float>> f = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f).take(4);
            }
        };
        Utils.expectStreamEvents("Test JBacon.fromArray (take 4)",
                f,
                1.0f, 2.0f, 3.0f, 4.0f);
    }

    @Test(timeout = 100)
    public void testFromArrayFilter() {
        final F<EventStream<Float>> f1 = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f).filter(false);
            }
        };
        final F<EventStream<Float>> f2 = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f).filter(true);
            }
        };
        Utils.expectStreamEvents("Test JBacon.fromArray (filter false)",
                f1,
                new Float[]{});
        Utils.expectStreamEvents("Test JBacon.fromArray (filter true)",
                f2,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
    }

    @Test(timeout = 100)
    public void testFromArrayMap() {
        final F1<Float, Float> multTwo = new F1<Float, Float>() {
            @Override
            public Float run(Float val) {
                return val * 2.0f;
            }
        };
        final F<EventStream<Float>> f = new F<EventStream<Float>>() {
            @Override
            public EventStream<Float> run() {
                return JBacon.fromArray(1.0f, 2.0f, 3.0f, 4.0f).map(multTwo);
            }
        };
        Utils.expectStreamEvents("Test JBacon.fromArray (map val * 2)",
                f,
                2.0f, 4.0f, 6.0f, 8.0f);
    }
}
