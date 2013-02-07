package jbacon;

import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.Streamable;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.LinkedList;
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

    protected static final LinkedList<Future<Void>> futures = new LinkedList<Future<Void>>();
    public static final ScheduledExecutorService intervalScheduler = Executors.newScheduledThreadPool(1);

    public static final int STREAMABLE_UPDATE_TIME = 5;
    public static final int CHECK_FUTURE_DELAY = 30;

    static {
        intervalScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                for(final Streamable<?> s : Streamable.allStreamables) {
                    s.update();
                }
            }
        }, STREAMABLE_UPDATE_TIME, STREAMABLE_UPDATE_TIME, TimeUnit.MILLISECONDS);
        intervalScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                for(Future<Void> future : futures) {
                    if(future.isDone()) {
                        try {
                            future.get();
                        } catch (InterruptedException e) {
                        } catch (ExecutionException e) {
                            System.err.println("Exception during scheduled JBacon runnable");
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                }
            }
        }, CHECK_FUTURE_DELAY, CHECK_FUTURE_DELAY, TimeUnit.MILLISECONDS);

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
                    JBacon.intervalScheduler.shutdownNow();
                }
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
            @Override
            protected void onSubscribe() {
                this.distribute(onceEvent);
                this.distribute(endEvent);
            }
        };
        return ret;
    }

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
        final Event<T> initial = vals.length > 0 ? new Event.Initial<T>(vals[0]) : new Event.End<T>();
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected void onSubscribe() {
                System.out.println(uid + ": distributing initial");
                this.distribute(initial);
                for(int i = 1; i < vals.length; i++) {
                    Event<T> next = new Event.Next<T>(vals[i]);
                    this.distribute(next);
                }
                if(vals.length > 0) {
                    this.distribute(new Event.End<T>());
                }
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
            private long lastTime;

            private Runnable timer = new Runnable() {
                @Override
                public void run() {
                    if(firstEvent != null) {
                            distribute(firstEvent);
                            firstEvent = null;
                            lastTime = System.nanoTime();
                    }
                    else {
                            long interval = System.nanoTime() - lastTime;
                            lastTime = System.nanoTime();
                            distribute(new Event.Next<Long>(interval));
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
        };
        return ret;
    }

    public static <T> EventStream<T> interval(final long delay, final TimeUnit timeUnit, final T val) {
        final EventStream<T> ret = new EventStream<T>() {
            Event<T> firstEvent = new Event.Initial<T>(val);

            @Override
            protected void onSubscribe() {
                JBacon.intervalScheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if(firstEvent != null) {
                            distribute(firstEvent);
                            firstEvent = null;
                        }
                        else {
                            distribute(new Event.Next<T>(val));
                        }
                    }
                }, delay, delay, timeUnit);
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
        public Long run(Long val) {
            return TimeUnit.MILLISECONDS.convert(val, TimeUnit.NANOSECONDS);
        }
    };

    /**
     * For use with <code>JBacon.interval(...).map(intervalInSeconds)<code/>. Converts all events from
     * an interval EventStream to seconds.
     */
    public static final F1<Long, Float> intervalInSeconds = new F1<Long, Float>() {
        @Override
        public Float run(Long val) {
            return val / 1000000000.0f;
        }
    };

    public static <T> EventStream<T> sequentially(final long interval, final TimeUnit timeUnit, final T... vals) {
        final Event.Initial<T> initial = new Event.Initial<T>(vals[0]);
        final EventStream<T> ret = new EventStream<T>() {
            private boolean isRunning = false;
            private Event<T> firstEvent = initial;
            private int cur = 0;

            private Runnable timer = new Runnable() {
                @Override
                public void run() {
                    if(firstEvent != null) {
                        distribute(firstEvent);
                        firstEvent = null;
                        if(vals.length == 1) {
                            distribute(new Event.End<T>());
                        }
                        else {
                            JBacon.intervalScheduler.schedule(timer, interval, timeUnit);
                        }
                    }
                    else {
                        cur++;
                        T next = vals[cur];
                        distribute(new Event.Next<T>(next));
                        if(cur == vals.length - 1) {
                            distribute(new Event.End());
                        }
                        else {
                            JBacon.intervalScheduler.schedule(timer, interval, timeUnit);
                        }
                    }
                }
            };

            @Override
            protected void onSubscribe() {
                if(!this.isRunning) {
                    this.isRunning = true;
                    JBacon.intervalScheduler.schedule(this.timer, interval, timeUnit);
                }
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
            private int cur = 0;

            private Runnable timer = new Runnable() {
                @Override
                public void run() {
                    if(firstEvent != null) {
                        distribute(firstEvent);
                        firstEvent = null;
                    }
                    else {
                        cur = (cur + 1) % vals.length;
                        T next = vals[cur];
                        distribute(new Event.Next<T>(next));
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
            @Override
            protected void onSubscribe() {
                System.out.println("JBacon.later: scheduling distribution");
                ScheduledFuture<Void> future = intervalScheduler.schedule(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        distribute(onceEvent);
                        distribute(endEvent);
                        return null;
                    }
                }, delay, timeUnits);
                futures.push(future);
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
                        public Void run(T val) {
                            distribute(new Event.Initial<T>(val));
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
