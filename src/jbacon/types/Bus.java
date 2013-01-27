package jbacon.types;

import jbacon.JBacon;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/23/13
 * Time: 5:51 PM
 */
public class Bus<T> extends EventStream<T> {
    protected boolean ended = false;
    protected Object childrenLock = new Object();
    protected LinkedList<EventStream<T>> children = new LinkedList<EventStream<T>>();

    public void push(Event<T> e) {
        this.broadcastEvent(e);
        this.distribute(e);
    }

    public void end() {
        if(this.ended) return;
        this.ended = true;
        final Event<T> end = new Event.End<T>();
        synchronized (this.childrenLock) {
            for(final EventStream<T> stream : this.children) {
                JBacon.threading.submit(new Runnable() {
                    @Override
                    public void run() {
                        stream.distribute(end);
                    }
                });
            }
            this.children.clear();
        }
    }

    public void error(Event.Error<T> e) {
        this.broadcastEvent(e);
    }

    private void broadcastEvent(final Event<T> e) {
        if(this.ended) return;
        synchronized (childrenLock) {
            for(final EventStream<T> stream : this.children) {
                JBacon.threading.submit(new Runnable() {
                    @Override
                    public void run() {
                        String ret = stream.distribute(e);
                        if(ret.equals(Event.noMore)) {
                            Bus.this.unplug(stream);
                        }
                    }
                });
            }
        }
    }

    public void plug(EventStream<T> stream) {
        if(this.ended) return;
        synchronized (this.childrenLock) {
            this.children.push(stream);
        }
    }

    protected void unplug(EventStream<T> stream) {
        synchronized (this.childrenLock) {
            this.children.remove(stream);
        }
    }
}
