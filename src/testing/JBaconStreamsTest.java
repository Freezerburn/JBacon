package testing;

import jbacon.JBacon;
import jbacon.interfaces.F2;
import jbacon.types.Event;
import jbacon.types.EventStream;
import junit.framework.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.*;
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
        final boolean[] incomplete = new boolean[]{true};
        final long laterDelay = 100;
        // Q: Why does there have to be a 40ms error buffer?
        // A: Because it takes ~30ms to do the JBacon.later(...).onValue(...) call chain. Hrm.
        //    Oh, the first call to JBacon.later takes a while (probably to generate code or something, not
        //    sure what the JVM is doing). Calling it once beforehand reduces the amount of time it takes
        //    to a basically negligible level.
        //    The bulk of the time is spent between calling the function the first time and the function
        //    actually getting started (10-20ms)
        JBacon.later(laterDelay, TimeUnit.MILLISECONDS, "lol");
        final long laterDelayWithError = laterDelay + 10;
        final long expectedTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(laterDelayWithError, TimeUnit.MILLISECONDS);
        EventStream<String> later = JBacon.later(laterDelay, TimeUnit.MILLISECONDS, "lol");
        later.onValue(new F2<String, Boolean, String>() {
            @Override
            public String run(String val1, Boolean val2) throws Exception {
                if (!val2) {
                    long curTime = System.nanoTime();
                    assertEquals("Later - val", val1, "lol");
                    assertFalse("Later - isEnd", val2);
                    assertTrue("Later - timing", expectedTime > curTime);
                    incomplete[0] = false;
                    return Event.more;
                }
                return Event.noMore;
            }
        });
        while(incomplete[0]) {
                Thread.yield();
        }
    }

    // Currently cannot test this with an error in the stream
    @Test(timeout = 300)
    public void testSequentially1() {
        final boolean[] incomplete = new boolean[]{true};
        final long sequentiallyDelay = 100;
        final long sequentiallyDelayWithError1 = sequentiallyDelay + 10;
        final long sequentiallyDelayWithError2 = sequentiallyDelay + 110;
        JBacon.sequentially(sequentiallyDelay, TimeUnit.MILLISECONDS, "lol", "wut");
        final long expectedTime1 = System.nanoTime() + TimeUnit.NANOSECONDS.convert(sequentiallyDelayWithError1, TimeUnit.MILLISECONDS);
        final long expectedTime2 = System.nanoTime() + TimeUnit.NANOSECONDS.convert(sequentiallyDelayWithError2, TimeUnit.MILLISECONDS);
        JBacon.sequentially(sequentiallyDelay, TimeUnit.MILLISECONDS, "lol", "wut").onValue(new F2<String, Boolean, String>() {
            int cur = 0;
            @Override
            public String run(String val1, Boolean val2) throws Exception {
                long curTime = System.nanoTime();
                if(val2) {
                    assertFalse("End came too soon", cur < 2);
                    assertNull("Still have value when ended", val1);
                    incomplete[0] = false;
                    return Event.noMore;
                }
                if(cur == 0) {
                    assertEquals("Sequentially - first val", val1, "lol");
                    assertTrue("Sequentially - first timing", expectedTime1 > curTime);
                }
                else if(cur == 1) {
                    assertEquals("Sequentially - second val", val1, "wut");
                    assertTrue("Sequentially - second timing", expectedTime2 > curTime);
                }
                else if(cur > 1) {
                    fail("Too many values sent");
                }
                cur++;
                return Event.more;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        while(incomplete[0]) {
            Thread.yield();
        }
    }

    @Test
    public void testInterval() {
        final boolean[] incomplete = new boolean[]{true};
        JBacon.interval(100, TimeUnit.MILLISECONDS, "lol");
        JBacon.interval(100, TimeUnit.MILLISECONDS, "lol").take(3).onValue(new F2<String, Boolean, String>() {
            int cur = 0;
            long expected = System.nanoTime() + TimeUnit.NANOSECONDS.convert(110, TimeUnit.MILLISECONDS);
            @Override
            public String run(String val1, Boolean val2) throws Exception {
                long curTime = System.nanoTime();
                if(cur > 2) {
                    assertNull(val1);
                    assertTrue("Interval - ended", val2);
                    incomplete[0] = false;
                    return Event.noMore;
                }
                assertEquals("Interval - val " + cur, val1, "lol");
                assertFalse("Interval - not ended", val2);
                assertTrue("Interval - timing " + cur, expected > curTime);
                cur++;
                expected = System.nanoTime() + TimeUnit.NANOSECONDS.convert(110, TimeUnit.MILLISECONDS);
                return Event.more;
            }
        });
        while(incomplete[0]) {
            Thread.yield();
        }
    }
}
