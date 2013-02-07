package testing;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 2/6/13
 * Time: 11:40 AM
 */
public class Utils {
    public static final boolean DEBUG = true;

    public static<T> EventStream<T> series(final long delayInMillis,
                                           final T... values) {
        return JBacon.sequentially(delayInMillis, TimeUnit.MILLISECONDS, values);
    }

    public static <T> EventStream<T> series(final long delay,
                                            final TimeUnit timeUnit,
                                            final T... values) {
        return JBacon.sequentially(delay, timeUnit, values);
    }

    public static <T> void expectStreamEvents(final String description,
                                              final F<EventStream<T>> f,
                                              final T... expected) {
        final long startTime = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
        try {
            final boolean[] done = new boolean[]{false};
            final EventStream<T> toTest = f.run();
            toTest.subscribe(new F1<Event<T>, String>() {
                boolean gotInitial = false;
                int cur = 0;
                @Override
                public String run(Event<T> val) throws Exception {
                    if(val.hasValue()) {
                        if(!gotInitial) {
                            if(DEBUG) {
                                System.out.println(description + ": init.assertTrue(" + val.isInitial() + ")");
                            }
                            assertTrue(val.isInitial());
                            gotInitial = true;
                        }
                        if(DEBUG) {
                            System.out.println(description + ": assertEquals(" + val.getValue() + ", " + expected[cur] + ")");
                        }
                        assertEquals(val.getValue(), expected[cur]);
                        cur++;
                    }
                    else if(val.isEnd()) {
                        if(DEBUG) {
                            System.out.println(description + ": assertTrue(" + cur + " == " + expected.length + ")");
                        }
                        assertTrue(cur == expected.length);
                        done[0] = true;
                        return JBacon.noMore;
                    }
                    return JBacon.more;
                }
            });
            while(!done[0]) {
                Thread.yield();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(description + ": test took " +
                (TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS) - startTime) +
                "ms");
    }

    public static <T> void expectStreamEvents(final String description,
                                              final F<EventStream<T>> f,
                                              final long initialDelay,
                                              final long betweenDelay,
                                              final TimeUnit timeUnit,
                                              final T... expected) {
        final long startTime = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
        try {
            final boolean[] done = new boolean[]{false};
            // Make sure that the act of getting the EventStream doesn't take a while.
            // Calling something such as JBacon.later(...) can take ~10-30ms the first time.
            f.run();
            final EventStream<T> toTest = f.run();
            toTest.subscribe(new F1<Event<T>, String>() {
                boolean gotInitial = false;
                int cur = 0;
                long expectedTime = initialDelay == 0 ? 0 : System.nanoTime() + TimeUnit.NANOSECONDS.convert(initialDelay, timeUnit);
                @Override
                public String run(Event<T> val) throws Exception {
                    final long curTime = System.nanoTime();
                    if(val.hasValue()) {
                        if(!gotInitial) {
                            if(DEBUG) {
                                System.out.println(description + ": init.assertTrue(" + val.isInitial() + ")");
                            }
                            assertTrue(val.isInitial());
                            gotInitial = true;
                        }
                        if(DEBUG) {
                            System.out.println(description + ": assertEquals(" + val.getValue() + ", " + expected[cur] + ")");
                        }
                        assertEquals(val.getValue(), expected[cur]);
                        // If the desired initial delay is 0, then we let it pass without checking. This is here
                        // so that if an Event.Initial is expected, you don't have to put in 10ms or something
                        // like that.
                        if(expectedTime > 0) {
                            if(DEBUG) {
                                System.out.println(description + ": val.assertTrue(" + expectedTime + " > " + curTime + ") = " +
                                        (expectedTime > curTime) + (expectedTime > curTime ? " by " + (expectedTime - curTime) : ""));
                            }
                            assertTrue(expectedTime > curTime);
                        }
                        else if(DEBUG) {
                            System.out.println(description + ": val.ignoreTime");
                        }
                        cur++;
                    }
                    else if(val.isEnd()) {
                        if(DEBUG) {
                            System.out.println(description + ": end.assertTrue(" + expectedTime + " > " + curTime + ") = " +
                                    (expectedTime > curTime) + (expectedTime > curTime ? " by " + (expectedTime - curTime) : ""));
                        }
                        assertTrue(expectedTime > curTime);
                        if(DEBUG) {
                            System.out.println(description + ": assertTrue(" + cur + " == " + expected.length + ")");
                        }
                        assertTrue(cur == expected.length);
                        done[0] = true;
                        return JBacon.noMore;
                    }
                    expectedTime = System.nanoTime() + TimeUnit.NANOSECONDS.convert(betweenDelay, timeUnit);
                    return JBacon.more;
                }
            });
            while(!done[0]) {
                Thread.yield();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(description + ": test took " +
                (TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS) - startTime) +
                "ms");
    }
}
