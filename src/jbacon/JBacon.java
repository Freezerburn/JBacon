package jbacon;

import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.Streamable;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.LinkedList;
import java.util.TimerTask;
import java.util.concurrent.*;

/**
 * A utility class that is used to create a myriad of types of EventStreams, ranging from an EventStream that
 * immediately ends to an EventStream that pushes values at regular intervals. <br/>
 *
 * Please note that if you use JBacon, in order for your program to shut down properly, a System.exit call
 * must be made for shutdown hooks JBacon creates upon program startup. This cleanly stops concurrent StreamEvent
 * tasks from running, thus allowing the program to actually stop.
 *
 * User: Vincent "Freezerburn" Kuyatt
 * Date: 1/24/13
 * Time: 1:46 AM
 */
public class JBacon {
    public static final String noMore = "veggies!";
    public static final String more = "moar bacon!";
    public static final String pass = "good bacon!";
    public static final String noPass = "bad bacon!";
    protected static final int numThreads = 3;
    public static final ExecutorService threading = Executors.newFixedThreadPool(numThreads);
    public static final ScheduledExecutorService intervalScheduler = Executors.newScheduledThreadPool(1);
    public static final Object futuresLock = new Object();
    public static final LinkedList<Future<?>> futures = new LinkedList<Future<?>>();
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
        final LinkedList<Future<?>> removeLater = new LinkedList<Future<?>>();
        intervalScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                synchronized (futuresLock) {
                    for(Future<?> future : futures) {
                        try {
                            future.get(2, TimeUnit.MILLISECONDS);
                            if(future.isDone()) {
                                removeLater.push(future);
                            }
                            Thread.yield();
                        } catch (ExecutionException e) {
                            e.getCause().printStackTrace();
                            System.exit(1);
                        } catch (CancellationException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
//                            e.printStackTrace();
                        }
                    }
                    for(Future<?> future : removeLater) {
                        futures.remove(future);
                    }
                    removeLater.clear();
                }
            }
        }, 100, 30, TimeUnit.MILLISECONDS);
//        streamableUpdater.setDaemon(true);
        // Make sure all our threads get shut down when the program quits.
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Shutting down JBacon scheduler...");
                    JBacon.intervalScheduler.shutdown();
                    if(!JBacon.intervalScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        System.out.println("Forcing JBacon scheduler shutdown");
                        JBacon.intervalScheduler.shutdownNow();
                    }
                }
                catch(InterruptedException e) {
                    System.out.println("Forcing JBacon scheduler shutdown");
                    JBacon.threading.shutdownNow();
                }
                try {
                    System.out.println("Shutting down JBacon threads...");
                    JBacon.threading.shutdown();
                    if(!JBacon.threading.awaitTermination(3, TimeUnit.SECONDS)) {
                        System.out.println("Forcing JBacon thread shutdown");
                        JBacon.threading.shutdownNow();
                    }
                }
                catch(InterruptedException e) {
                    System.out.println("Forcing JBacon thread shutdown");
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

    /**
     * Creates an EventStream that pushes the passed value to the first subscriber once, and then
     * immediately ends.
     * @param val The value to be pushed to the first subscriber.
     * @param <T> The type of the value.
     * @return The EventStream that will push the value.
     */
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
                return (this.canDistribute && !event.isEnd()) ? JBacon.pass : JBacon.noPass;
            }
        };
        return ret;
    }

    /*
    Thar be dragons here, edit with care! I've had to put in a few different hacks to account for
    different types of subscriber streams, so screwing around with it can have bad results. Possibly
    freezing your program entirely.
     */
    /**
     * Creates an EventStream that will pass all parameters to the first subscriber, then immediately
     * end. <br/>
     *
     * BUG: The parameters given to fromArray have a chance of being given to the first subscriber
     * out of order.
     * @param vals The values to be pushed to the first subscriber.
     * @param <T> The type of the values.
     * @return An EventStream that pushes the parameters to the first subscriber.
     */
    public static <T> EventStream<T> fromArray(final T... vals) {
        final SynchronousQueue<T> queue = new SynchronousQueue<T>(true);
        final Event<T> initial = vals.length > 0 ? new Event.Initial<T>(null) {
            @Override
            public T getValue() {
                try {
                    T ret = queue.take();
                    return ret;
                } catch (InterruptedException e) {
                }
                return null;
            }
        } : new Event.End<T>();
        final EventStream<T> ret = new EventStream<T>() {
            private boolean canTake = false;
            private boolean skipNext = false;
            private boolean ended = false;

            @Override
            protected void distributeFail(final boolean end) {
                if(end) {
                    ended = true;
                }
                else {
                    skipNext = true;
                }
            }

            @Override
            protected void onSubscribe() {
                this.canTake = true;
                this.distribute(initial);
                if(ended) {
                    this.distribute(new Event.End<T>());
                    return;
                }
                if(vals.length > 0 && !skipNext) {
                    try {
                        queue.put(vals[0]);
                    } catch (InterruptedException e) {
                    }
                }
                skipNext = false;
                for(int i = 1; i < vals.length; i++) {
                    Event<T> next = new Event.Next<T>(null) {
                        @Override
                        public T getValue() {
                            try {
                                T ret = queue.take();
                                return ret;
                            } catch (InterruptedException e) {
                            }
                            return null;
                        }
                    };
                    this.distribute(next);
                    if(ended) {
                        this.distribute(new Event.End<T>());
                        return;
                    }
                    try {
                        if(!skipNext) {
                            queue.put(vals[i]);
                        }
                    } catch (InterruptedException e) {
                    }
                    skipNext = false;
                }
                if(vals.length > 0) {
                    this.distribute(new Event.End<T>());
                }
                this.canTake = false;
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(this.canTake) {
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        return ret;
    }

    /**
     * Creates an EventStream that at the specified interval, will pass the time delta in nanoseconds
     * to any subscribers. Note that this can vary slightly due to inaccuracies in the system clock, or
     * possibly other reasons. It should be pretty close to the actual interval specified, but not
     * exactly. <br/>
     *
     * Please note that Event.Initial will be fired with a time delta of 0.
     * @param interval The time to delay each event fire.
     * @param timeUnit The units of time for the interval.
     * @return The EventStream that can be subscribed to which fires time deltas in nanoseconds.
     */
    public static EventStream<Long> interval(final long interval, final TimeUnit timeUnit) {
        final long millisInterval = TimeUnit.MILLISECONDS.convert(interval, timeUnit);
        final Event.Initial<Long> initial = new Event.Initial<Long>(0L);
        final EventStream<Long> ret = new EventStream<Long>() {
            private boolean isRunning = false;
            private Event<Long> firstEvent = initial;
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
                                distribute(new Event.Next<Long>(interval));
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
                    JBacon.intervalScheduler.scheduleAtFixedRate(this.timer, millisInterval, millisInterval, TimeUnit.MILLISECONDS);
                }
            }

            @Override
            protected String onDistribute(final Event<Long> event) {
                if(event.isEnd()) {
                    this.timer.cancel();
                }
                if(this.canTake) {
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        return ret;
    }

    public static <T> EventStream<T> interval(final long delay, final TimeUnit timeUnit, final T val) {
        final EventStream<T> ret = new EventStream<T>() {
            boolean canDistribute = false;
            Event<T> firstEvent = new Event.Initial<T>(val);
            @Override
            protected void onSubscribe() {
                JBacon.intervalScheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        canDistribute = true;
                        if(firstEvent != null) {
                            distribute(firstEvent);
                            firstEvent = null;
                        }
                        else {
                            distribute(new Event.Next<T>(val));
                        }
                        canDistribute = false;
                    }
                }, delay, delay, timeUnit);
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(canDistribute) return JBacon.pass;
                return JBacon.noPass;
            }
        };
        return ret;
    }

    /**
     * For use with <code>JBacon.interval(...).map(intervalInMillis)<code/>. Converts all events from
     * an interval EventStream to milliseconds.
     */
    public static final F1<Long, Long> intervalInMillis = new F1<Long, Long>() {
        @Override
        public Long run(Long val) throws Exception {
            return TimeUnit.MILLISECONDS.convert(val, TimeUnit.NANOSECONDS);
        }
    };

    /**
     * For use with <code>JBacon.interval(...).map(intervalInSeconds)<code/>. Converts all events from
     * an interval EventStream to seconds.
     */
    public static final F1<Long, Float> intervalInSeconds = new F1<Long, Float>() {
        @Override
        public Float run(Long val) throws Exception {
            return val / 1000000000.0f;
        }
    };

    public static <T> EventStream<T> sequentially(long interval, TimeUnit timeUnit, final T... vals) {
        final long millisInterval = TimeUnit.MILLISECONDS.convert(interval, timeUnit);
        final Event.Initial<T> initial = new Event.Initial<T>(vals[0]);
        final EventStream<T> ret = new EventStream<T>() {
            private boolean isRunning = false;
            private Event<T> firstEvent = initial;
            private final Object takeLock = new Object();
            private boolean canTake = false;
            private int cur = 0;
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
                                cur++;
                                T next = vals[cur];
                                distribute(new Event.Next<T>(next));
                                if(cur == vals.length - 1) {
                                    Thread.yield();
                                    distribute(new Event.End());
                                }
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
                    JBacon.intervalScheduler.scheduleAtFixedRate(this.timer, millisInterval, millisInterval, TimeUnit.MILLISECONDS);
                }
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(event.isEnd()) {
                    this.timer.cancel();
                    return JBacon.noMore;
                }
                if(this.canTake) {
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        return ret;
    }

    public static <T> EventStream<T> repeatedly(long interval, TimeUnit timeUnit, final T... vals) {
        final long millisInterval = TimeUnit.MILLISECONDS.convert(interval, timeUnit);
        final Event.Initial<T> initial = new Event.Initial<T>(vals[0]);
        final EventStream<T> ret = new EventStream<T>() {
            private boolean isRunning = false;
            private Event<T> firstEvent = initial;
            private final Object takeLock = new Object();
            private boolean canTake = false;
            private int cur = 0;
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
                                cur = (cur + 1) % vals.length;
                                T next = vals[cur];
                                distribute(new Event.Next<T>(next));
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
                    JBacon.intervalScheduler.scheduleAtFixedRate(this.timer, millisInterval, millisInterval, TimeUnit.MILLISECONDS);
                }
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(event.isEnd()) {
                    this.timer.cancel();
                }
                if(this.canTake) {
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        return ret;
    }

    public static <T> EventStream<T> never() {
        return new EventStream<T>() {
            @Override
            protected void onSubscribe() {
                this.distribute(new Event.End<T>());
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                return JBacon.noMore;
            }
        };
    }

    // This is DOM-specific in Bacon. Can I make my own version of this?
//    public static <T> EventStream<T> fromEventTarget(target, event)

    // TODO: Make this compliant with function construction rules
    public static <T> EventStream<T> fromPoll(long interval, F<Event<T>> f) {
        return null;
    }

    public static <T> EventStream<T> later(final long delay, final TimeUnit timeUnits, final T val) {
        final Event.Initial<T> onceEvent = new Event.Initial<T>(val);
        final Event.End<T> endEvent = new Event.End<T>();
        final EventStream<T> ret = new EventStream<T>() {
            private boolean canDistribute = false;

            @Override
            protected void onSubscribe() {
                System.out.println("JBacon.later: scheduling distribution");
                ScheduledFuture<Void> future = intervalScheduler.schedule(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        canDistribute = true;
                        distribute(onceEvent);
                        distribute(endEvent);
                        canDistribute = false;
                        return null;
                    }
                }, delay, timeUnits);
                synchronized (futuresLock) {
                    futures.push(future);
                }
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                return (this.canDistribute && !event.isEnd()) ? JBacon.pass : JBacon.noPass;
            }
        };
        return ret;
    }

    public static <T> EventStream<T> fromCallback(final F1<F1<T, Void>, T> callback) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected void onSubscribe() {
                try {
                    callback.run(new F1<T, Void>() {
                        @Override
                        public Void run(T val) throws Exception {
                            distribute(new Event.Initial<T>(val));
                            Thread.yield();
                            distribute(new Event.End<T>());
                            return null;
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        return ret;
    }
}
