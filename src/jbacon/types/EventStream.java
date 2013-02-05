package jbacon.types;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.interfaces.Observable;

import java.util.LinkedList;

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
        return Event.pass;
    }

    protected void distributeFail() {
        if(parent != null) {
            parent.distributeFail();
        }
    }

    protected void asyncEventRun(final Event<T> val) {
        for(final F1<Event<T>, String> subscriber : this.eventSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final String ret = subscriber.run(val);
                    if(ret.equals(Event.noMore)) {
                        System.out.println(uid + ": Unsubscribing event listener " + subscriber);
                        EventStream.this.unsubscribe(subscriber);
                    }
                }
            });
        }
    }

    protected void asyncValueRun(final Event<T> val) {
        for(final F2<T, Boolean, String> subscriber : this.valueSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final String ret = subscriber.run(val.isEnd() ? null : val.getValue(), val.isEnd());
                    if(ret.equals(Event.noMore)) {
                        System.out.println(uid + ": Unsubscribing value listener " + subscriber);
                        EventStream.this.onValueUnsubscribe(subscriber);
                    }
                }
            });
        }
    }

    protected void streamTake(final Event<T> val) {
        for(final EventStream<T> stream : this.returnedStreams) {
//            JBacon.threading.submit(new Runnable() {
//                @Override
//                public void run() {
                    final String ret = stream.distribute(val);
                    if(ret.equals(Event.noMore)) {
                        System.out.println(uid + ": Unsubscribing stream " + stream.uid);
                        EventStream.this.streamUnsubscribe(stream);
                    }
//                }
//            });
        }
    }

    protected void asyncErrorRun(final Event<T> val) {
        for(final F1<String, Event.ErrRet<T>> subscriber : this.errorSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final Event.ErrRet<T> ret = subscriber.run(val.getError());
                    if(ret.eventStatus.equals(Event.noMore)) {
                        System.out.println(uid + ": Unsubscribing error listener " + subscriber);
                        EventStream.this.errorUnsubscribe(subscriber);
                    }
                    EventStream.this.distribute(new Event.Next<T>(ret.ret));
                }
            });
        }
    }

    protected void endStream(final Event<T> optional) {
        System.out.println(this.uid + ": ending stream" +
                " e(" + eventSubscribers.size() + ")" +
                " v(" + valueSubscribers.size() + ")" +
                " s(" + returnedStreams.size() + ")");
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
        System.out.println(this.uid + ": ended" +
                " e(" + eventSubscribers.size() + ")" +
                " v(" + valueSubscribers.size() + ")" +
                " s(" + returnedStreams.size() + ")");
    }

    protected String distribute(final Event<T> val) {
        if(this.ended) return Event.noMore;

        final String todo = this.onDistribute(val);
        // *** END HANDLING
        if(val.isEnd() || todo.equals(Event.noMore)) {
            this.distributeFail();
            this.endStream(val);
            return Event.noMore;
        }
        else if(todo.equals(Event.noPass)) {
            this.distributeFail();
            return todo;
        }
        // *** VALUE HANDLING
        else if(val.hasValue()) {
            synchronized (eventLock) {
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

        return Event.more;
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
        synchronized (this.streamLock) {
            this.returnedStreams.remove(stream);
        }
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
        final EventStream<K> ret = new EventStream<K>();
        // Takes in events through the current EventStream and converts them to a value accepted by the
        // returned EventStream before pushing them to the returned EventStream.
        final EventStream<T> intermediary = new EventStream<T>() {
            @Override
            protected String distribute(final Event<T> event) {
                Event<K> newEvent;
                if(event.isInitial()) {
                    newEvent = new Event.Initial<K>(getFromVal.run(event.getValue()));
                }
                else if(event.isNext()) {
                    newEvent = new Event.Next<K>(getFromVal.run(event.getValue()));
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
                event.val = func.run();
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
                    return Event.pass;
                }
                return Event.noPass;
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
                    if(func.run(event.val)) {
                        return super.distribute(event);
                    }
                }
                return Event.more;
            }
        };
        ret.parent = this;
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;
    }
}
