/**
 * Lamport implementation, refer to http://amturing.acm.org/p558-lamport.pdf
 * for details.
 */
package cn.v5;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wy96fyw@gmail.com on 2017/7/18.
 */
public class DistributedProcess extends Thread {
   private final int dpid;
   private final LamportClock clock;
   private final CoordinatorMain cm;
   private volatile boolean stop = false;
   private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
   private final ThreadPoolExecutor workingProcess = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
   private final AtomicInteger midGenerator = new AtomicInteger(0);
   private final CyclicBarrier cb;
   private final LinkedBlockingQueue<Message> recvQueue = new LinkedBlockingQueue<Message>();
   private final Thread recvThread;

   // request message for every process
   private Map<Integer, List<Message>> midToMsg = new HashMap<Integer, List<Message>>();
   // latest message for every process
   private Map<Integer, Message> pidToMsg = new ConcurrentHashMap<Integer, Message>();

   public DistributedProcess(int dpid, CyclicBarrier cb, CoordinatorMain cm) {
      this.cb = cb;
      this.cm = cm;
      this.dpid = dpid;
      this.clock = new LamportClock();
      // increment clock time every 1 sec
      this.scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
         public void run() {
            clock.increment();
         }
      }, 0, 1, TimeUnit.SECONDS);
      this.recvThread = new Thread(new Runnable() {
         public void run() {
            while (!stop) {
               try {
                  Message m = recvQueue.take();
                  process(m);
               } catch (InterruptedException e) {
                  continue;
               }
            }
         }
      });
      this.recvThread.start();
   }

   public int getDpid() {
      return dpid;
   }

   private void addMessage(Message m) {
      synchronized (midToMsg) {
         if (midToMsg.containsKey(m.getFrom())) {
            midToMsg.get(m.getFrom()).add(m);
         } else {
            List<Message> msgs = new ArrayList<Message>();
            msgs.add(m);
            midToMsg.put(m.getFrom(), msgs);
         }
      }
   }

   private void dropMessage(Message drop) {
      synchronized (midToMsg) {
         if (midToMsg.containsKey(drop.getFrom())) {
            Iterator<Message> messageIterator = midToMsg.get(drop.getFrom()).iterator();
            while (messageIterator.hasNext()) {
               Message m = messageIterator.next();
               if (m.getMid().compareTo(drop.getMid()) == 0) {
                  messageIterator.remove();
               }
            }
         }
      }
   }

   // compare request message according to lamport clock time and process id
   private boolean compareClock(Message m1, Message m2) {
      if (m1.getTimestamp() < m2.getTimestamp() ||
            (m1.getTimestamp() == m2.getTimestamp() && m1.getFrom() < m2.getFrom())) {
         return true;
      }
      return false;
   }

   private void tryToAcquireResource() {
      synchronized (midToMsg) {
         if (midToMsg.containsKey(dpid) && !midToMsg.get(dpid).isEmpty()) {
            Message myMessage = midToMsg.get(dpid).get(0);
            int i = 1;
            // condition (ii) of rule 5
            for (Map.Entry<Integer, Message> entry : pidToMsg.entrySet()) {
               if (entry.getKey() != dpid) {
                  if (!compareClock(myMessage, entry.getValue())) {
                     return;
                  }
                  i++;
               }
            }
            if (!cm.hasAcceptedAll(i)) return;

            // condition (i) of rule 5
            for (Map.Entry<Integer, List<Message>> entry : midToMsg.entrySet()) {
               if (entry.getKey() != dpid && !entry.getValue().isEmpty()) {
                  if (!compareClock(myMessage, entry.getValue().get(0))) {
                     return;
                  }
               }
            }

            // remove this request message
            final Message firstMsg = midToMsg.get(dpid).remove(0);
            workingProcess.execute(new Runnable() {
               public void run() {
                  cm.acquire(firstMsg.getMid(), dpid, firstMsg.getTimestamp());
                  // emulate owning resources for a long time
                  try {
                     Thread.sleep(50L);
                     // rule 3
                     cm.release(firstMsg.getMid(), dpid, firstMsg.getTimestamp());
                     sendAll(new Message(dpid, firstMsg.getMid(), Message.MessageType.RELEASE_RES, clock.time()));
                  } catch (InterruptedException e) {
                     e.printStackTrace();
                  }

               }
            });
         }
      }
   }

   public void receive(Message m) throws InterruptedException {
      recvQueue.put(m);
   }

   private void process(Message m) throws InterruptedException {
      clock.update(m.getTimestamp());
      pidToMsg.put(m.getFrom(), m);
      switch (m.getmType()) {
         case REQUEST_RES:
            // rule 2
            addMessage(m);
            send(new Message(dpid, m.getMid(), Message.MessageType.REQUEST_ACK, clock.time()), m.getFrom(), false);
            break;
         case REQUEST_ACK:
            break;
         case RELEASE_RES:
            // rule 4
            dropMessage(m);
            break;
         default:
            break;
      }
      tryToAcquireResource();
   }

   private synchronized void send(Message m, int pid, boolean exclude) throws InterruptedException {
      pidToMsg.put(this.dpid, m);
      cm.sendMsg(m, pid, exclude);
      clock.increment();
   }

   private void sendAll(Message m) throws InterruptedException {
      send(m, dpid, true);
   }

   public void shutdown() throws InterruptedException {
      stop = true;
      this.interrupt();
      this.recvThread.interrupt();
      scheduledExecutorService.shutdown();
      scheduledExecutorService.awaitTermination(10, TimeUnit.MILLISECONDS);

      workingProcess.shutdown();
      workingProcess.awaitTermination(10, TimeUnit.MILLISECONDS);
   }

   /**
    * request resource in a random interval
    */
   @Override
   public void run() {
      // wait util all processes created
      try {
         this.cb.await();
      } catch (InterruptedException e) {
         e.printStackTrace();
      } catch (BrokenBarrierException e) {
         e.printStackTrace();
      }
      Random r = new SecureRandom();
      while (!stop) {
         int nextSend = 10 + r.nextInt(20);
         try {
            Thread.sleep(nextSend);
            // rule 1
            Message message = new Message(dpid, Integer.toString(midGenerator.getAndIncrement()), Message.MessageType.REQUEST_RES, clock.time());
            addMessage(message);
            sendAll(message);
         } catch (InterruptedException e) {
            continue;
         }
      }
   }
}
