package cn.v5;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import static java.lang.System.exit;

/**
 * Created by wy96fyw@gmail.com on 2017/7/18.
 */
public class CoordinatorMain {
   private Map<Integer, DistributedProcess> processMap = new HashMap<Integer, DistributedProcess>();
   private volatile int occupied = -1;

   public static void main(String[] args) throws InterruptedException {
      if (args.length != 1) {
         System.out.println("Usage: [process_num]");
         exit(1);
      }
      CoordinatorMain coordinator = new CoordinatorMain();
      int num = Integer.parseInt(args[0]);
      CyclicBarrier cb = new CyclicBarrier(num);
      for (int i = 0; i < num; i++) {
         DistributedProcess p = new DistributedProcess(i, cb, coordinator);
         p.start();
         coordinator.processMap.put(i, p);
      }
      Thread.sleep(100000L);
      for (DistributedProcess p : coordinator.processMap.values()) {
         p.shutdown();
      }
      exit(0);
   }

   /**
    *
    * @param m message
    * @param pid
    * @param exclude true means send to other than this pid
    */
   public void sendMsg(Message m, int pid, boolean exclude) throws InterruptedException {
      if (exclude) {
         for (DistributedProcess p : processMap.values()) {
            if (p.getDpid() != pid) {
               p.receive(m);
            }
         }
      } else if (processMap.containsKey(pid)) {
         processMap.get(pid).receive(m);
      }
   }

   public boolean hasAcceptedAll(int num) {
      return num == processMap.size();
   }

   public synchronized void release(String mid, int pid, long time) {
      if (occupied != pid) {
         System.out.println("p " + pid + ", m: " + mid + ", t: " + time + " release resource" + ", but occupied by p " + occupied);
      }
      occupied = -1;
      System.out.println("p " + pid + ", m: " + mid + ", t: " + time + " release resource");
   }

   public synchronized void acquire(String mid, int pid, long time) {
      if (occupied != -1) {
         System.out.println("p " + pid + ", m: " + mid + ", t: " + time + " acquire resource" + ", but occupied by p " + occupied);
      }
      occupied = pid;
      System.out.println("p " + pid + ", m: " + mid + ", t: " + time + " acquire resource");
   }
}
