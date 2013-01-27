import jbacon.JBacon;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.types.Bus;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/25/13
 * Time: 7:10 PM
 */
public class Main {
    public static void main(String[] args) {
        EventStream<Float> test = JBacon.once((Number) 500).map(new F1<Number, Float>() {
            @Override
            public Float run(Number val) {
                System.out.println("Map: val " + val + " to " + (val.longValue() / 100.0f));
                return val.longValue() / 100.0f;
            }
        });
        test.onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val, Boolean isEnd) {
                System.out.println("once.onValue: " + val + " end? " + isEnd);
                if (isEnd) {
                    return Event.noMore;
                }
                return Event.more;
            }
        });

        EventStream<Long> test1 = JBacon.interval(100);
        test1.onValue(new F2<Long, Boolean, String>() {
            protected int numTimes = 0;
            @Override
            public String run(Long val1, Boolean val2) {
                if(val2) return Event.noMore;
                System.out.println("Interval received " + val1);
                System.out.println(TimeUnit.MILLISECONDS.convert(val1, TimeUnit.NANOSECONDS));
                numTimes++;
                if(numTimes > 4) {
                    System.out.println("Interval limit hit, ending");
                    return Event.noMore;
                }
                return Event.more;
            }
        });

        EventStream<Long> test2 = JBacon.fromArray();
        EventStream<Long> test3 = JBacon.fromArray(10L, 20L, 30L);
        test2.onValue(new F2<Long, Boolean, String>() {
            @Override
            public String run(Long val1, Boolean val2) {
                System.out.println("FromArray empty: val=" + val1 + ", isEnd=" + val2);
                return Event.more;
            }
        });
        test3.onValue(new F2<Long, Boolean, String>() {
            @Override
            public String run(Long val1, Boolean val2) {
                System.out.println("FromArray non-empty: val=" + val1 + ", isEnd=" + val2);
                return Event.more;
            }
        });

        Bus<Long> timerBus = new Bus<Long>();
        timerBus.plug(test1);

        try {
            Thread.sleep(1600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        timerBus.end();
        System.exit(0);
    }
}
