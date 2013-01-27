package jbacon;

import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.interfaces.Streamable;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.Timer;
import java.util.TimerTask;
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
    public static final Timer intervalScheduler = new Timer(true);
    public static final int STREAMABLE_UPDATE_TIME = 5;
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

    public static <T> EventStream<T> once(final T val) {
        final Event.Initial<T> onceEvent = new Event.Initial<T>(val);
        final Event.End<T> endEvent = new Event.End<T>();
        final EventStream<T> ret = new EventStream<T>() {
            private boolean canDistribute = false;

            @Override
            protected void onSubscribe() {
                this.canDistribute = true;
                this.distribute(onceEvent);
                this.distribute(endEvent);
                this.canDistribute = false;
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                return (this.canDistribute && !event.isEnd()) ? Event.pass : Event.noPass;
            }
        };
        return ret;
    }

    public static <T> EventStream<T> fromArray(final T... vals) {
        final Event<T> initial = vals.length > 0 ? new Event.Initial<T>(vals[0]) : new Event.End<T>();
        final EventStream<T> ret = new EventStream<T>() {
            private boolean canTake = false;

            @Override
            protected void onSubscribe() {
                this.canTake = true;
                this.distribute(initial);
                for(int i = 1; i < vals.length; i++) {
                    this.distribute(new Event.Next<T>(vals[i]));
                }
                if(vals.length > 0) {
                    this.distribute(new Event.End<T>());
                }
                this.canTake = false;
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(this.canTake) {
                    return Event.pass;
                }
                return Event.noPass;
            }
        };
        return ret;
    }

    public static <T extends Number> EventStream<T> interval(final long interval) {
        final Event.Initial<T> initial = new Event.Initial<T>((T) new Long(0));
        final EventStream<T> ret = new EventStream<T>() {
            private boolean isRunning = false;
            private Event<T> firstEvent = initial;
            private final Object takeLock = new Object();
            private boolean canTake = false;
            private long lastTime;
            private TimerTask timer = new TimerTask() {
                @Override
                public void run() {
                    if(!eventSubscribers.isEmpty() ||
                            !valueSubscribers.isEmpty() ||
                            !errorSubscribers.isEmpty() ||
                            !returnedStreams.isEmpty()) {
                        if(firstEvent != null) {
                            synchronized (takeLock) {
                                canTake = true;
                                distribute(firstEvent);
                                canTake = false;
                                firstEvent = null;
                                lastTime = System.nanoTime();
                            }
                        }
                        else {
                            synchronized (takeLock) {
                                canTake = true;
                                long interval = System.nanoTime() - lastTime;
                                lastTime = System.nanoTime();
                                distribute(new Event.Next<T>((T) new Long(interval)));
                                canTake = false;
                            }
                        }
                    }
                }
            };

            @Override
            protected void onSubscribe() {
                if(!this.isRunning) {
                    this.isRunning = true;
                    JBacon.intervalScheduler.schedule(this.timer, interval, interval);
                }
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(event.isEnd()) {
                    this.timer.cancel();
                }
                if(this.canTake) {
                    return Event.pass;
                }
                return Event.noPass;
            }
        };
        return ret;
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
