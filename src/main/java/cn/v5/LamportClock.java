package cn.v5;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Created by wy96fyw@gmail.com on 2017/7/18.
 */
public class LamportClock {
   private volatile long time;
   private static final AtomicLongFieldUpdater<LamportClock> updater = AtomicLongFieldUpdater.newUpdater(LamportClock.class, "time");

   public LamportClock() {
      time = new Long(1);
   }

   public void increment() {
      while (true) {
         long expect = time;
         long update = expect + 1;
         if (updater.compareAndSet(this, expect, update)) {
            break;
         }
      }
   }

   public long time() {
      return time;
   }

   public void update(long t) {
      while (true) {
         long expect = time;
         if (t + 1 > expect) {
            if (updater.compareAndSet(this, expect, t + 1)) {
               break;
            }
         } else {
            break;
         }
      }
   }
}
