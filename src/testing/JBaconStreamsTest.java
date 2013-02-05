package testing;

import jbacon.JBacon;
import jbacon.interfaces.F2;
import jbacon.types.Event;
import jbacon.types.EventStream;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 2/5/13
 * Time: 10:33 AM
 */
public class JBaconStreamsTest {
    @Test
    public void testLater1() {
        final boolean[] incomplete = new boolean[]{true};
        final long laterDelay = 100;
        // Q: Why does there have to be a 40ms error buffer?
        // A: Because it takes ~30ms to do the JBacon.later(...).onValue(...) call chain. Hrm.
        //    Oh, the first call to JBacon.later takes a while (probably to generate code or something, not
        //    sure what the JVM is doing). Calling it once beforehand reduces the amount of time it takes
        //    to a basically negligible level.
        //    The bulk of the time is spent between calling the function the first time and the function
        //    actually getting started (10-20ms)
        final long laterDelayWithError = laterDelay + 10;
        JBacon.later(laterDelay, TimeUnit.MILLISECONDS, "lol");
        final long expectedTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(laterDelayWithError, TimeUnit.MILLISECONDS);
        EventStream<String> later = JBacon.later(laterDelay, TimeUnit.MILLISECONDS, "lol");
        later.onValue(new F2<String, Boolean, String>() {
            @Override
            public String run(String val1, Boolean val2) throws Exception {
                if (!val2) {
                    long curTime = System.nanoTime();
                    assertEquals("Later1 - val", val1, "lol");
                    assertFalse("Later1 - isEnd", val2);
                    assertTrue("Later1 - timing", expectedTime > curTime);
                    incomplete[0] = false;
                    return Event.more;
                }
                return Event.noMore;
            }
        });
        while(incomplete[0] == true) {
                Thread.yield();
        }
    }
}
