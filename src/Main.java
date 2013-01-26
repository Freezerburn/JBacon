import jbacon.JBacon;
import jbacon.interfaces.F1;
import jbacon.types.Bus;
import jbacon.types.Event;
import jbacon.types.EventStream;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/25/13
 * Time: 7:10 PM
 */
public class Main {
    public static void main(String[] args) {
        EventStream<Long> test = JBacon.interval(500);
        test.onValue(new F1<Long, String>() {
            @Override
            public String run(Long val) {
                System.out.println("Interval: " + val);
                return Event.more;
            }
        });
        Bus<Long> testBus = new Bus<Long>();
        testBus.plug(test);
        try {
            Thread.sleep(1600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        testBus.end();
        System.exit(0);
    }
}
