package jbacon.types.impl;

import jbacon.types.Bus;
import jbacon.types.Event;

/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/23/13
 * Time: 11:50 PM
 */
public class TickBus extends Bus<Float> implements Runnable {
    protected Thread tickThread;
    protected long millis;

    public TickBus(long millis) {
        this.millis = millis;
        this.tickThread = new Thread(this);
        this.tickThread.start();
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        this.push(new Event.Initial<Float>(0.0f));
        try{
            while(true) {
                float delta = (System.nanoTime() - last) / 1000000000.0f;
                last = System.nanoTime();
                if(delta > 0.0f) {
//                    System.out.println("Pushing " + delta + " to TickBus");
                    this.push(new Event.Next<Float>(delta));
                }
                Thread.sleep(this.millis);
            }
        } catch (InterruptedException e) {
            // Thread's over folks!
        }
    }

    @Override
    public void end() {
        super.end();
        this.tickThread.interrupt();
    }
}
