package jbacon;

import jbacon.interfaces.F;
import jbacon.interfaces.Promise;
import jbacon.interfaces.Streamable;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/24/13
 * Time: 1:46 AM
 */
public class JBacon {
    protected static final int numThreads = 3;
    public static final ExecutorService threading = Executors.newFixedThreadPool(numThreads);
    public static final int STREAMABLE_UPDATE_TIME = 16;
    private static Thread streamableUpdater;

    static {
        streamableUpdater = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isRunning = true;
                while(isRunning) {
                    try {
                        for(final Streamable<?> s : Streamable.allStreamables) {
                            JBacon.threading.submit(new Runnable() {
                                @Override
                                public void run() {
                                    // This should hopefully prevent two threads from running the same update
                                    // function at the same time and stomping on each other, if that should ever
                                    // happen.
                                    synchronized (s) {
                                        s.update();
                                    }
                                }
                            });
                        }
                        Thread.sleep(STREAMABLE_UPDATE_TIME);
                    } catch (InterruptedException e) {
                        System.out.println("Streamable update thread interrupted, stopping.");
                        isRunning = false;
                    }
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Shutting down JBacon threads...");
                    JBacon.threading.shutdown();
                    if(!JBacon.threading.awaitTermination(3, TimeUnit.SECONDS)) {
                        System.out.println("Forcing stream thread shutdown");
                        JBacon.threading.shutdownNow();
                    }
                }
                catch(InterruptedException e) {
                    System.out.println("Forcing stream thread shutdown");
                    JBacon.threading.shutdownNow();
                }
                System.out.println("Shutting down Streamable update thread...");
                streamableUpdater.interrupt();
            }
        }));
    }

    // TODO: IMPLEMENTATIONS YAY
    // TODO: Full compliance with function construction rules
    // TODO: Testing all of these
    // TODO: Implementing and testing more features in EventStreams in general
    // TODO: Simplification of EventStream? If Possible?
    // TODO: Implement Property
    // TODO: Better Bus/EventStream interaction?
    // TODO: Better Bus in general?
    // TODO: Easier way to create custom EventStreams? So that these functions aren't hacky?
    // Don't know what a Promise is supposed to be yet, so we'll just leave this commented for now.
    //  Maybe something that eventually gets a value, and when it does emits an event then end?
//    public static <T> EventStream<T> fromPromise(Promise<T> promise) {
//        return null;
//    }

    public static <T> EventStream<T> once(T val) {
        final Event.Initial<T> onceEvent = new Event.Initial<T>(val);
        final EventStream<T> ret = new EventStream<T>() {
            private boolean hasTaken = false;

            @Override
            protected String take(final Event<T> event) {
                if(!this.eventSubscribers.isEmpty() ||
                        !this.valueSubscribers.isEmpty() ||
                        !this.errorSubscribers.isEmpty()) {
                    // Don't pass another End event if noMore was already returned.
                    // Assumes that End is always passed to subscribers when noMore gets returned.
                    if(super.take(onceEvent).equals(Event.noMore)) {
                        return Event.noMore;
                    }
                    return super.take(new Event.End<T>());
                }
                return Event.more;
            }
        };
        return ret;
    }

    public static <T> EventStream<T> fromArray(T... vals) {
        return null;
    }

    public static <T> EventStream<T> interval(long interval, T val) {
        return null;
    }

    public static <T> EventStream<T> sequentially(long interval, T... vals) {
        return null;
    }

    public static <T> EventStream<T> repeatedly(long interval, T... vals) {
        return null;
    }

    public static <T> EventStream<T> never() {
        return null;
    }

    // This is DOM-specific in Bacon. Can I make my own version of this?
//    public static <T> EventStream<T> fromEventTarget(target, event)

    // TODO: Make this compliant with function construction rules
    public static <T> EventStream<T> fromPoll(long interval, F<Event<T>> f) {
        return null;
    }

    public static <T> EventStream<T> later(long delay, T val) {
        return null;
    }
}
