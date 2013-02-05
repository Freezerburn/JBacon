package jbacon.interfaces;

import jbacon.types.EventStream;
import jbacon.types.Property;

import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/23/13
 * Time: 1:20 PM
 */
public interface Observable<T> {
    /**
     * Maps a constant value to an EventStream, so that whenever a value gets pushed to this Observer,
     * it will distribute the constant value to the returned EventStream.
     * @param val The constant value to distribute to the returned EventStream.
     * @return An EventStream that only emits the value val.
     */
    public EventStream<T> map(T val);

    /**
     * Maps a constant value to an EventStream, but the F1 getFromVal is used to do something to the value,
     * whether that's getting some value from an Object or converting it to something else.
     * @param getFromVal The function which operates on the value and the return value gets pushed to the EventStream.
     * @param <K> The type of the value to get pushed to the EventStream.
     * @return
     */
    public <K> EventStream<K> map(F1<T, K> getFromVal);
    public EventStream<T> map(F<T> func);

//    /**
//     * Maps the current value of the property to values which get pushed into the EventStream.
//     * @param p
//     * @return
//     */
//    public EventStream<T> map(Property<T> p);
//
//    /**
//     * Any errors that happen in the EventStream are handled by the passed function and pushed into
//     * the returned EventStream.
//     * @param funcOnError
//     * @param <K>
//     * @return
//     */
//    public <K> EventStream<K> mapError(F1<T, K> funcOnError);
//
//    public EventStream<T> mapEnd(T val);
//    public <K> EventStream<K> mapEnd(F1<T, K> funcOnEnd);
//
    public EventStream<T> filter(boolean val);
    public EventStream<T> filter(F1<T, Boolean> func);
//    public EventStream<T> filter(T val, F1<T, Boolean> getFromVal);
//
//    /**
//     * Pushes values to the returned EventStream ONLY if the given property is true at the time of the event.
//     * @param p
//     * @return
//     */
//    public EventStream<T> filter(Property<Boolean> p);

    public EventStream<T> takeWhile(F1<T, Boolean> func);
    public EventStream<T> take(int num);
    public <K> EventStream<T> takeUntil(EventStream<K> stream);
    public EventStream<T> skip(int num);

    public EventStream<T> delay(long delay, TimeUnit timeUnit);
}
