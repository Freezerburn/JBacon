import jbacon.JBacon;
import jbacon.interfaces.F1;
import jbacon.interfaces.F2;
import jbacon.types.Bus;
import jbacon.types.Event;
import jbacon.types.EventStream;

import java.util.ArrayList;
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

        EventStream<Long> test1 = JBacon.interval(100, TimeUnit.MILLISECONDS);
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
        test1.map(JBacon.intervalInSeconds).onValue(new F2<Float, Boolean, String>() {
            protected int numTimes = 0;
            @Override
            public String run(Float val1, Boolean val2) {
                if(val2) return Event.noMore;
                System.out.println("Interval in sec: " + val1);
                numTimes++;
                if(numTimes > 4) return Event.noMore;
                return Event.more;
            }
        });

        EventStream<Long> test2 = JBacon.fromArray();
        EventStream<Long> test3 = JBacon.fromArray(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        test2.onValue(new F2<Long, Boolean, String>() {
            @Override
            public String run(Long val1, Boolean val2) {
                System.out.println("FromArray empty: val=" + val1 + ", isEnd=" + val2);
                return Event.more;
            }
        });
        System.out.println("AFTER FROMARRAY EMPTY");
        test3.filter(false).onValue(new F2<Long, Boolean, String>() {
            ArrayList<Long> vals = new ArrayList<Long>();

            @Override
            public String run(Long val1, Boolean val2) {
                if (val2) {
                    System.out.println("FromArray non-empty: " + vals);
                    return Event.noMore;
                } else {
                    vals.add(val1);
                    return Event.more;
                }
//                System.out.println("FromArray non-empty: val=" + val1 + ", isEnd=" + val2);
            }
        });
        System.out.println("AFTER FROMARRAY.FILTER");

        Bus<Long> timerBus = new Bus<Long>();
        timerBus.plug(test1);

        EventStream<Float> test4 = JBacon.sequentially(500, TimeUnit.MILLISECONDS, 3.0f);
        test4.onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("ES1-Sequentially: " + val1);
                return Event.more;
            }
        });
        EventStream<Float> test5 = JBacon.sequentially(500, TimeUnit.MILLISECONDS, 1.0f, 2.0f);
        test5.onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("ES2-Sequentially: " + val1);
                return Event.more;
            }
        });

        EventStream<Float> test6 = JBacon.repeatedly(500, TimeUnit.MILLISECONDS, 4.0f);
        EventStream<Float> test7 = JBacon.repeatedly(500, TimeUnit.MILLISECONDS, 5.0f, 6.0f);
        test6.onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("ES1-Repeatedly: " + val1);
                return Event.more;
            }
        });
        test7.onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("ES2-Repeatedly: " + val1);
                return Event.more;
            }
        });
        test7.takeWhile(new F1<Float, Boolean>() {
            @Override
            public Boolean run(Float val) {
                System.out.println("takeWhile: " + val + ", " + (val < 6.0f));
                return val < 6.0f;
            }
        }).onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("takeWhile.onValue: " + val1 + ", " + val2);
                return Event.more;
            }
        });
        test7.takeUntil(test6.delay(1000, TimeUnit.MILLISECONDS)).onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("takeUntil: " + val1 + ", " + val2);
                return Event.more;
            }
        });
        test7.throttle(100, TimeUnit.MILLISECONDS).onValue(new F2<Float, Boolean, String>() {
            @Override
            public String run(Float val1, Boolean val2) {
                System.out.println("throttle.onValue: " + val1 + ", " + val2);
                return Event.more;
            }
        });

        JBacon.never().onValue(new F2<Object, Boolean, String>() {
            @Override
            public String run(Object val1, Boolean val2) {
                System.out.println("Never: " + val1 + ", " + val2);
                return Event.more;
            }
        });

        JBacon.later(300, TimeUnit.MILLISECONDS, 100).onValue(new F2<Integer, Boolean, String>() {
            @Override
            public String run(Integer val1, Boolean val2) {
                System.out.println("Later: " + val1);
                return Event.noMore;
            }
        });

        EventStream<Long> test8 = JBacon.fromArray(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        EventStream<Long> test9 = JBacon.fromArray(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        test8.take(3).onValue(new F2<Long, Boolean, String>() {
            ArrayList<Long> test = new ArrayList<Long>();
            @Override
            public String run(Long val1, Boolean val2) {
                if(val2) {
                    System.out.println("take.onValue: " + test);
                    return Event.noMore;
                }
                else {
                    test.add(val1);
                }
                return Event.more;
            }
        });
        test9.skip(3).onValue(new F2<Long, Boolean, String>() {
            ArrayList<Long> test = new ArrayList<Long>();

            @Override
            public String run(Long val1, Boolean val2) {
                if (val2) {
                    System.out.println("skip.onValue: " + test);
                    return Event.noMore;
                } else {
                    test.add(val1);
                }
                return Event.more;
            }
        });

        try {
            Thread.sleep(1600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        timerBus.end();
        System.exit(0);
    }
}
