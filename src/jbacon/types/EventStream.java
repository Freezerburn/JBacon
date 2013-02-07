package jbacon.types;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.interfaces.Observable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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

    protected final Object valueLock = new Object();
    protected LinkedList<F2<T, Boolean, String>> valueSubscribers = new LinkedList<F2<T, Boolean, String>>();

    protected final Object eventLock = new Object();
    protected LinkedList<F1<Event<T>, String>> eventSubscribers = new LinkedList<F1<Event<T>, String>>();

    protected final Object errorLock = new Object();
    protected LinkedList<F1<String, Event.ErrRet<T>>> errorSubscribers = new LinkedList<F1<String, Event.ErrRet<T>>>();

    protected final Object streamLock = new Object();
    protected LinkedList<EventStream<T>> returnedStreams = new LinkedList<EventStream<T>>();
    protected ArrayList<EventStream<T>> unsubscribeLater = new ArrayList<EventStream<T>>(50);

    // ***
    // The following are several methods that can be used to influence the way an EventStream works
    // without touching the inner workings of the EventStream itself. This allows us to easily create
    // things such as JBacon.once without having to override the distribute method.
    // ***

    protected void onSubscribe() {
        if(parent != null) {
            System.out.println(uid + ": sending onSubscribe to parent");
            parent.onSubscribe();
        }
    }

    protected String onDistribute(final Event<T> event) {
        return JBacon.pass;
    }

    protected String onDistribute(final Future<Event<T>> future) {
        return JBacon.pass;
    }

    protected void distributeFail(final boolean end) {
        if(parent != null) {
            parent.distributeFail(end);
        }
    }

    protected void asyncEventRun(final Event<T> val) {
        for(final F1<Event<T>, String> subscriber : this.eventSubscribers) {
            String ret = null;
            ret = subscriber.run(val);
            if(ret.equals(JBacon.noMore)) {
//                System.out.println(uid + ": Unsubscribing event listener " + subscriber);
                EventStream.this.unsubscribe(subscriber);
            }
        }
    }

    protected void asyncValueRun(final Event<T> val) {
        for(final F2<T, Boolean, String> subscriber : this.valueSubscribers) {
            String ret;
            synchronized (subscriber) {
                ret = subscriber.run(val.isEnd() ? null : val.getValue(), val.isEnd());
            }
            if(ret.equals(JBacon.noMore)) {
//                System.out.println(uid + ": Unsubscribing value listener " + subscriber);
                EventStream.this.onValueUnsubscribe(subscriber);
            }
        }
    }

    protected void streamTake(final Event<T> val) {
        for(final EventStream<T> toRemove : this.unsubscribeLater) {
            synchronized (this.streamLock) {
                this.returnedStreams.remove(toRemove);
            }
        }
        this.unsubscribeLater.clear();
        for(final EventStream<T> stream : this.returnedStreams) {
//            JBacon.threading.submit(new Runnable() {
//                @Override
//                public void run() {
                    String ret;
//                    synchronized (stream) {
                        ret = stream.distribute(val);
//                    }
                    if(ret.equals(JBacon.noMore)) {
//                        System.out.println(uid + ": Unsubscribing stream " + stream.uid);
                        EventStream.this.streamUnsubscribe(stream);
                    }
//                }
//            });
        }
    }

    protected void asyncErrorRun(final Event<T> val) {
        for(final F1<String, Event.ErrRet<T>> subscriber : this.errorSubscribers) {
            final Event.ErrRet<T> ret = subscriber.run(val.getError());
            if(ret.eventStatus.equals(JBacon.noMore)) {
//                System.out.println(uid + ": Unsubscribing error listener " + subscriber);
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
        synchronized (this.eventLock) {
            this.asyncEventRun(end);
            this.eventSubscribers.clear();
        }
        synchronized (this.errorLock) {
            this.errorSubscribers.clear();
        }
        synchronized (this.valueLock) {
            this.asyncValueRun(end);
            this.valueSubscribers.clear();
        }
        synchronized (this.streamLock) {
            this.streamTake(end);
            this.returnedStreams.clear();
        }
//        System.out.println(this.uid + ": ended" +
//                " e(" + eventSubscribers.size() + ")" +
//                " v(" + valueSubscribers.size() + ")" +
//                " s(" + returnedStreams.size() + ")");
    }

    protected String distribute(final Event<T> val) {
        if(this.ended) return JBacon.noMore;

        final String todo = this.onDistribute(val);
        System.out.println(uid + ": onDistribute " + todo);
        // *** END HANDLING
        if(val.isEnd() || todo.equals(JBacon.noMore)) {
            this.distributeFail(true);
            this.endStream(null);
            return JBacon.noMore;
        }
        else if(todo.equals(JBacon.noPass)) {
            this.distributeFail(false);
            return todo;
        }
        // *** VALUE HANDLING
        else if(val.hasValue()) {
//            System.out.println(uid + ": value " + val.getValue());
            synchronized (eventLock) {
                System.out.println(uid + ": sending value async");
                this.asyncEventRun(val);
            }
            synchronized (valueLock) {
                this.asyncValueRun(val);
            }
            synchronized (this.streamLock) {
                this.streamTake(val);
            }
        }
        // *** ERROR HANDLING
        else if(val.isError()) {
            synchronized (eventLock) {
                this.asyncEventRun(val);
            }
            synchronized (errorLock) {
                this.asyncErrorRun(val);
            }
            synchronized (this.streamLock) {
                this.streamTake(val);
            }
        }

        return JBacon.more;
    }

    protected String distribute(final Future<Event<T>> future) {
        if(this.ended) return JBacon.noMore;

        // *** END HANDLING
        if(future.isDone()) {
            try {
                return this.distribute(future.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        else {
            JBacon.intervalScheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    distribute(future);
                }
            }, 2, TimeUnit.MILLISECONDS);
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
        synchronized (this.valueLock) {
            this.valueSubscribers.push(f);
        }
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
        synchronized (this.eventLock) {
            System.out.println(uid + ": pushing subscriber");
            this.eventSubscribers.push(f);
        }
        this.onSubscribe();
        return ret;
    }


    private void onValueUnsubscribe(final F2<T, Boolean, String> f) {
        if(this.ended) return;
        synchronized (this.valueLock) {
            this.valueSubscribers.remove(f);
        }
    }

    private void unsubscribe(final F1<Event<T>, String> f) {
        if(this.ended) return;
        synchronized (this.eventLock) {
            this.eventSubscribers.remove(f);
        }
    }

    private void errorUnsubscribe(final F1<String, Event.ErrRet<T>> f) {
        if(this.ended) return;
        synchronized (this.errorLock) {
            this.errorSubscribers.remove(f);
        }
    }

    private void streamUnsubscribe(final EventStream<T> stream) {
        if(this.ended) return;
        this.unsubscribeLater.add(stream);
//        synchronized (this.streamLock) {
//            this.returnedStreams.remove(stream);
//        }
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
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;
    }

    @Override
    public <K> EventStream<K> map(final F1<T, K> getFromVal) {
        final EventStream<K> ret = new EventStream<K>() {
            @Override
            protected String distribute(final Event<K> event) {
                System.out.println(uid + ": distribute inner");
                return super.distribute(event);
            }
        };
        // Takes in events through the current EventStream and converts them to a value accepted by the
        // returned EventStream before pushing them to the returned EventStream.
        final EventStream<T> intermediary = new EventStream<T>() {
            @Override
            protected String distribute(final Event<T> event) {
                System.out.println("map event");
                Event<K> newEvent;
                System.out.println("Starting value tranformation async");
                if(event.isInitial()) {
                    try {
                        System.out.println("initial event " + event.getValue());
                        newEvent = new Event.Initial<K>(getFromVal.run(event.getValue()));
                        System.out.println("initial event 2 " + newEvent.getValue());
                    } catch (Exception e) {
                        System.out.println("error event");
                        newEvent = new Event.Error<K>(e.toString());
                    }
                }
                else if(event.isNext()) {
                    try {
                        System.out.println("next event");
                        newEvent = new Event.Next<K>(getFromVal.run(event.getValue()));
                    } catch (Exception e) {
                        System.out.println("error event");
                        newEvent = new Event.Error<K>(e.toString());
                    }
                }
                else if(event.isEnd()) {
                    System.out.println("end event");
                    newEvent = new Event.End<K>();
                }
                else {
                    System.out.println("error event");
                    newEvent = new Event.Error<K>(event.getError());
                }
                // We don't even want to call our own distribute, we're merely here to transmit the transformed
                // Event to the returned EventStream.
                System.out.println("distribute");
                return ret.distribute(newEvent);
            }
        };
        intermediary.parent = this;
        ret.parent = intermediary;
        synchronized (this.streamLock) {
            this.returnedStreams.push(intermediary);
        }
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
        synchronized (streamLock) {
            this.returnedStreams.push(ret);
        }
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
        synchronized (streamLock) {
            this.returnedStreams.push(ret);
        }
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
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
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
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
                return JBacon.more;
            }
        };
        ret.parent = this;
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
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
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <K> EventStream<T> takeUntil(final EventStream<K> stream) {
        // Chance of two threads trying to access nextHappened at the same time, so we need
        // a lock for it.
        final Object happenLock = new Object();
        // Using an array for a final-yet-not-final-value so that it can be used in closures
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
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;  //To change body of implemented methods use File | Settings | File Templates.
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
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
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
            ArrayList<T> buffered = new ArrayList<T>();

            @Override
            protected void onSubscribe() {
                this.whenDelayStop = System.nanoTime() + actualDelay;
            }

            @Override
            protected String onDistribute(final Event<T> event) {
                if(this.whenDelayStop > 0 && System.nanoTime() > this.whenDelayStop) {
//                    System.out.println("delay distributing now");
                    return JBacon.pass;
                }
                return JBacon.noPass;
            }
        };
        ret.parent = this;
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
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
//                System.out.println("Scheduling event at " + TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS));
                JBacon.intervalScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (distributeLock) {
//                            System.out.println("Distributing event at " + TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS));
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
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;
    }

    @Override
    public EventStream<T> doAction(F1<T, Void> func) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public EventStream<T> scan(T seed, F1<T, T> accumulator) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
