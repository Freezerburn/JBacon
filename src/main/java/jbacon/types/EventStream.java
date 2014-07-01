package jbacon.types;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.interfaces.Observable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/23/13
 * Time: 1:24 PM
 */
public class EventStream<T> implements Observable<T> {
    protected static long nextUid = 0;
    protected final long uid = nextUid++;

    protected EventStream<?> parent;
    protected boolean ended = false;

    protected LinkedList<F2<T, Boolean, String>> valueSubscribers = new LinkedList<F2<T, Boolean, String>>();
    protected LinkedList<F1<Event<T>, String>> eventSubscribers = new LinkedList<F1<Event<T>, String>>();
    protected LinkedList<F1<String, Event.ErrRet<T>>> errorSubscribers = new LinkedList<F1<String, Event.ErrRet<T>>>();
    protected LinkedList<EventStream<T>> returnedStreams = new LinkedList<EventStream<T>>();

    protected ArrayList<EventStream<T>> unsubscribeStreamLater = new ArrayList<EventStream<T>>(5);
    protected ArrayList<F2<T, Boolean, String>> unsubscribeValueLater = new ArrayList<F2<T, Boolean, String>>(5);
    protected ArrayList<F1<Event<T>, String>> unsubscribeEventLater = new ArrayList<F1<Event<T>, String>>(5);
    protected ArrayList<F1<String, Event.ErrRet<T>>> unsubscribeErrorLater = new ArrayList<F1<String, Event.ErrRet<T>>>(5);

    // ***
    // The following are several methods that can be used to influence the way an EventStream works
    // without touching the inner workings of the EventStream itself. This allows us to easily create
    // things such as JBacon.once without having to override the distribute method.
    // ***
    protected void onSubscribe() {
        if(parent != null) {
            parent.onSubscribe();
        }
    }

    protected String onDistribute(final Event<T> event) {
        return JBacon.pass;
    }

    protected void distributeFail(final boolean end) {
        if(parent != null) {
            parent.distributeFail(end);
        }
    }
    // ***
    // End influential functions
    // ***

    protected void eventRun(final Event<T> val) {
        for(final F1<Event<T>, String> f : this.unsubscribeEventLater) {
            this.eventSubscribers.remove(f);
        }
        this.unsubscribeEventLater.clear();
        for(final F1<Event<T>, String> subscriber : this.eventSubscribers) {
            String ret = subscriber.run(val);
            if(ret.equals(JBacon.noMore)) {
                EventStream.this.unsubscribe(subscriber);
            }
        }
    }

    protected void valueRun(final Event<T> val) {
        for(final F2<T, Boolean, String> f : this.unsubscribeValueLater) {
            this.valueSubscribers.remove(f);
        }
        this.unsubscribeValueLater.clear();
        for(final F2<T, Boolean, String> subscriber : this.valueSubscribers) {
            String ret;
            ret = subscriber.run(val.isEnd() ? null : val.getValue(), val.isEnd());
            if(ret.equals(JBacon.noMore)) {
                EventStream.this.onValueUnsubscribe(subscriber);
            }
        }
    }

    protected void streamTake(final Event<T> val) {
        for(final EventStream<T> toRemove : this.unsubscribeStreamLater) {
            this.returnedStreams.remove(toRemove);
        }
        this.unsubscribeStreamLater.clear();
        for(final EventStream<T> stream : this.returnedStreams) {
            String ret;
            ret = stream.distribute(val);
            if(ret.equals(JBacon.noMore)) {
                EventStream.this.streamUnsubscribe(stream);
            }
        }
    }

    protected void errorRun(final Event<T> val) {
        for(final F1<String,Event.ErrRet<T>> f : this.unsubscribeErrorLater) {
            this.errorSubscribers.remove(f);
        }
        this.unsubscribeErrorLater.clear();
        for(final F1<String, Event.ErrRet<T>> subscriber : this.errorSubscribers) {
            final Event.ErrRet<T> ret = subscriber.run(val.getError());
            if(ret.eventStatus.equals(JBacon.noMore)) {
                EventStream.this.errorUnsubscribe(subscriber);
            }
            EventStream.this.distribute(new Event.Next<T>(ret.ret));
        }
    }

    protected void endStream(final Event<T> optional) {
//        System.out.println(this.uid + ": ending stream" +
//                " e(" + eventSubscribers.size() + ")" +
//                " v(" + valueSubscribers.size() + ")" +
//                " s(" + returnedStreams.size() + ")");

        this.ended = true;
        final Event<T> end = optional == null ? new Event.End<T>() : optional;

        this.eventRun(end);
        this.eventSubscribers.clear();

        this.errorSubscribers.clear();

        this.valueRun(end);
        this.valueSubscribers.clear();

        this.streamTake(end);
        this.returnedStreams.clear();

//        System.out.println(this.uid + ": ended" +
//                " e(" + eventSubscribers.size() + ")" +
//                " v(" + valueSubscribers.size() + ")" +
//                " s(" + returnedStreams.size() + ")");
    }

    protected String distribute(final Event<T> val) {
        if(this.ended) {
            return JBacon.noMore;
        }

        final String todo = this.onDistribute(val);

        // *** HANDLES: END Events
        if(val.isEnd() || todo.equals(JBacon.noMore)) {
            this.distributeFail(true);
            this.endStream(null);
            return JBacon.noMore;
        }
        else if(todo.equals(JBacon.noPass)) {
            this.distributeFail(false);
            return todo;
        }
        // *** HANDLES: INITIAL and NEXT Events
        else if(val.hasValue()) {
            this.eventRun(val);
            this.valueRun(val);
            this.streamTake(val);
        }
        // *** HANDLES: ERROR Events
        else if(val.isError()) {
            this.eventRun(val);
            this.errorRun(val);
            this.streamTake(val);
        }

        return JBacon.more;
    }

    public boolean isEnded() {
        return this.ended;
    }

    public Runnable onValue(final F2<T, Boolean, String> f) {
        if(this.ended) return null;
        final Runnable ret = new Runnable() {
            @Override
            public void run() {
                EventStream.this.onValueUnsubscribe(f);
            }
        };
        this.valueSubscribers.push(f);
        this.onSubscribe();
        return ret;
    }

    public Runnable subscribe(final F1<Event<T>, String> f) {
        if(this.ended) return null;
        final Runnable ret = new Runnable() {
            @Override
            public void run() {
                EventStream.this.unsubscribe(f);
            }
        };
        this.eventSubscribers.push(f);
        this.onSubscribe();
        return ret;
    }


    private void onValueUnsubscribe(final F2<T, Boolean, String> f) {
        this.unsubscribeValueLater.add(f);
    }

    private void unsubscribe(final F1<Event<T>, String> f) {
        this.unsubscribeEventLater.add(f);
    }

    private void errorUnsubscribe(final F1<String, Event.ErrRet<T>> f) {
        this.unsubscribeErrorLater.add(f);
    }

    private void streamUnsubscribe(final EventStream<T> stream) {
        this.unsubscribeStreamLater.add(stream);
    }

    @Override
    public EventStream<T> map(final T val) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String distribute(final Event<T> event) {
                // This should be safe because if the event retrieves a val, it will be the constant we want,
                // and if it isn't one that can retrieve a val, it'll throw an exception (or if handled correctly
                // it will just never be touched)
                event.val = val;
                return super.distribute(event);
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public <K> EventStream<K> map(final F1<T, K> getFromVal) {
        final EventStream<K> ret = new EventStream<K>() {
            @Override
            protected String distribute(final Event<K> event) {
                return super.distribute(event);
            }
        };
        // Takes in events through the current EventStream and converts them to a value accepted by the
        // returned EventStream before pushing them to the returned EventStream.
        final EventStream<T> intermediary = new EventStream<T>() {
            @Override
            protected String distribute(final Event<T> event) {
                Event<K> newEvent;
                if(event.isInitial()) {
                    try {
                        newEvent = new Event.Initial<K>(getFromVal.run(event.getValue()));
                    } catch (Exception e) {
                        newEvent = new Event.Error<K>(e.toString());
                    }
                }
                else if(event.isNext()) {
                    try {
                        newEvent = new Event.Next<K>(getFromVal.run(event.getValue()));
                    } catch (Exception e) {
                        newEvent = new Event.Error<K>(e.toString());
                    }
                }
                else if(event.isEnd()) {
                    newEvent = new Event.End<K>();
                }
                else {
                    newEvent = new Event.Error<K>(event.getError());
                }
                // We don't even want to call our own distribute, we're merely here to transmit the transformed
                // Event to the returned EventStream.
                return ret.distribute(newEvent);
            }
        };
        intermediary.parent = this;
        ret.parent = intermediary;
        this.returnedStreams.push(intermediary);
        return ret;
    }

    @Override
    public EventStream<T> map(final F<T> func) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String distribute(final Event<T> event) {
                try {
                    event.val = func.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return super.distribute(event);
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> filter(final boolean shouldAllow) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String onDistribute(final Event<T> event) {
                if(shouldAllow) {
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> filter(final F1<T, Boolean> func) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String distribute(final Event<T> event) {
                if(event.hasValue()) {
                    try {
                        if(func.run(event.val)) {
                            return super.distribute(event);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return JBacon.more;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> takeWhile(final F1<T, Boolean> func) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String onDistribute(final Event<T> event) {
                if(event.hasValue()) {
                    try {
                        if(func.run(event.getValue())) {
                            return JBacon.pass;
                        }
                        else {
                            return JBacon.noMore;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return JBacon.more;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> take(final int num) {
        final EventStream<T> ret = new EventStream<T>() {
            private int times = 0;

            @Override
            protected String onDistribute(final Event<T> event) {
                if(times >= num) {
                    return JBacon.noMore;
                }
                times++;
                return JBacon.pass;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public <K> EventStream<T> takeUntil(final EventStream<K> stream) {
        // Chance of two threads trying to access nextHappened at the same time, so we need
        // a lock for it.
        final Object happenLock = new Object();
        // Using an array for a final-yet-not-final-value so that it can be modified in closures
        // is my favorite hack :D
        final boolean[] nextHappened = new boolean[]{false};
        stream.subscribe(new F1<Event<K>, String>() {
            @Override
            public String run(Event<K> val) {
                if(val.isNext()) {
                    synchronized (happenLock) {
                        nextHappened[0] = true;
                    }
                    return JBacon.noMore;
                }
                return JBacon.more;
            }
        });
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            public String onDistribute(final Event<T> event) {
                synchronized (happenLock) {
                    if(nextHappened[0]) {
                        return JBacon.noMore;
                    }
                }
                return JBacon.more;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> skip(final int num) {
        final EventStream<T> ret = new EventStream<T>() {
            private int times = 0;

            @Override
            protected String onDistribute(final Event<T> event) {
                if(times >= num) {
                    return JBacon.pass;
                }
                times++;
                return JBacon.noPass;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    /**
     * Rejects all Events that happen until the given delay has passed.
     * @param delay The amount of delay expressed in TimeUnit time.
     * @param timeUnit The TimeUnit type of the passed-in delay.
     * @return An EventStream that rejects all Events for delay time.
     */
    @Override
    public EventStream<T> delay(long delay, TimeUnit timeUnit) {
        final long actualDelay = TimeUnit.NANOSECONDS.convert(delay, timeUnit);
        final EventStream<T> ret = new EventStream<T>() {
            long whenDelayStop = -1;

            @Override
            protected void onSubscribe() {
                this.whenDelayStop = System.nanoTime() + actualDelay;
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(this.whenDelayStop > 0 && System.nanoTime() > this.whenDelayStop) {
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> throttle(final long delay, final TimeUnit timeUnit) {
        final EventStream<T> ret = new EventStream<T>() {
            final Object distributeLock = new Object();
            boolean canDistribute = false;
            @Override
            protected String onDistribute(final Event<T> event) {
                if(canDistribute) {
                    return JBacon.pass;
                }
                JBacon.intervalScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (distributeLock) {
                            canDistribute = true;
                            distribute(event);
                            canDistribute = false;
                        }
                    }
                }, delay, timeUnit);
                return JBacon.noPass;
            }
        };
        ret.parent = this;
        this.returnedStreams.push(ret);
        return ret;
    }

    @Override
    public EventStream<T> doAction(F1<T, Void> func) {
        return null;
    }

    @Override
    public EventStream<T> scan(T seed, F1<T, T> accumulator) {
        return null;
    }
}
