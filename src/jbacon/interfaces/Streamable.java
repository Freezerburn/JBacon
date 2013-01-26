package jbacon.interfaces;

import jbacon.types.EventStream;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/24/13
 * Time: 1:47 AM
 */
public interface Streamable<T> {
    public static final LinkedList<Streamable<?>> allStreamables = new LinkedList<Streamable<?>>();

    public EventStream<T> asEventStream();
    public void update();
    // Why did I put this in here...?
//    public <K> EventStream<T> asEventStream(K... ks);
}
