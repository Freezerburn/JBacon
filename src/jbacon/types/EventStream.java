package jbacon.types;

import jbacon.JBacon;
import jbacon.interfaces.F;
import jbacon.interfaces.F1;
import jbacon.interfaces.Observable;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/23/13
 * Time: 1:24 PM
 */
public class EventStream<T> implements Observable<T> {
    protected boolean ended = false;

    protected Object valueLock = new Object();
    protected LinkedList<F1<T, String>> valueSubscribers = new LinkedList<F1<T, String>>();

    protected Object eventLock = new Object();
    protected LinkedList<F1<Event<T>, String>> eventSubscribers = new LinkedList<F1<Event<T>, String>>();

    protected Object errorLock = new Object();
    protected LinkedList<F1<String, Event.ErrRet<T>>> errorSubscribers = new LinkedList<F1<String, Event.ErrRet<T>>>();

    protected Object streamLock = new Object();
    protected LinkedList<EventStream<T>> returnedStreams = new LinkedList<EventStream<T>>();

    protected void asyncEventRunNoCheck(final Event<T> val) {
        for(final F1<Event<T>, String> subscriber : this.eventSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    subscriber.run(val);
                }
            });
        }
    }

    protected void asyncEventRun(final Event<T> val) {
        for(final F1<Event<T>, String> subscriber : this.eventSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final String ret = subscriber.run(val);
                    if(ret.equals(Event.noMore)) {
                        EventStream.this.unsubscribe(subscriber);
                    }
                }
            });
        }
    }

    protected void asyncValueRun(final Event<T> val) {
        for(final F1<T, String> subscriber : this.valueSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final String ret = subscriber.run(val.getValue());
                    if(ret.equals(Event.noMore)) {
                        EventStream.this.onValueUnsubscribe(subscriber);
                    }
                }
            });
        }
    }

    protected void asyncStreamtake(final Event<T> val) {
        for(final EventStream<T> stream : this.returnedStreams) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final String ret = stream.take(val);
                    if(ret.equals(Event.noMore)) {
                        EventStream.this.streamUnsubscribe(stream);
                    }
                }
            });
        }
    }

    protected void asyncErrorRun(final Event<T> val) {
        for(final F1<String, Event.ErrRet<T>> subscriber : this.errorSubscribers) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    final Event.ErrRet<T> ret = subscriber.run(val.getError());
                    if(ret.eventStatus.equals(Event.noMore)) {
                        EventStream.this.errorUnsubscribe(subscriber);
                    }
                    EventStream.this.take(new Event.Next<T>(ret.ret));
                }
            });
        }
    }

    protected void asyncStreamTakeNoCheck(final Event<T> val) {
        for(final EventStream<T> stream : this.returnedStreams) {
            JBacon.threading.submit(new Runnable() {
                @Override
                public void run() {
                    stream.take(val);
                }
            });
        }
    }

    protected String take(final Event<T> val) {
        if(this.ended) return Event.noMore;

        // TODO: Cleaner/better way to handle events?
        // *** END HANDLING
        if(val.isEnd()) {
            this.ended = true;
            synchronized (this.eventLock) {
                this.asyncEventRunNoCheck(val);
                this.eventSubscribers.clear();
            }
            synchronized (this.errorLock) {
                this.errorSubscribers.clear();
            }
            synchronized (this.valueLock) {
                this.valueSubscribers.clear();
            }
            synchronized (this.streamLock) {
                this.asyncStreamTakeNoCheck(val);
                this.returnedStreams.clear();
            }
            return Event.noMore;
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
                this.asyncStreamtake(val);
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
                this.asyncStreamtake(val);
            }
        }

        return Event.more;
    }

    public boolean isEnded() {
        return this.ended;
    }

    public Runnable onValue(final F1<T, String> f) {
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
        return ret;
    }


    private void onValueUnsubscribe(final F1<T, String> f) {
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
            protected String take(final Event<T> event) {
                // This should be safe because if the event retrieves a val, it will be the constant we want,
                // and if it isn't one that can retrieve a val, it'll throw an exception (or if handled correctly
                // it will just never be touched)
                event.val = val;
                return super.take(event);
            }
        };
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
        // TODO: Use a "lighter" structure? EvenStream is pretty heavy to allocate purely for transforming one thing
        final EventStream<T> intermediary = new EventStream<T>() {
            @Override
            protected String take(final Event<T> event) {
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
                // We don't even want to call our own take, we're merely here to transmit the transformed
                // Event to the returned EventStream.
                return ret.take(newEvent);
            }
        };
        synchronized (this.streamLock) {
            this.returnedStreams.push(intermediary);
        }
        return ret;
    }

    @Override
    public EventStream<T> map(final F<T> func) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String take(final Event<T> event) {
                event.val = func.run();
                return super.take(event);
            }
        };
        synchronized (streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;
    }

    @Override
    public EventStream<T> filter(final F1<T, Boolean> func) {
        final EventStream<T> ret = new EventStream<T>() {
            @Override
            protected String take(final Event<T> event) {
                if(event.hasValue()) {
                    if(func.run(event.val)) {
                        return super.take(event);
                    }
                }
                return Event.more;
            }
        };
        synchronized (this.streamLock) {
            this.returnedStreams.push(ret);
        }
        return ret;
    }
}
