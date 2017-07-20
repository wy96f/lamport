package cn.v5;

/**
 * Created by wy96fyw@gmail.com on 2017/7/18.
 */
public class Message {
   public static enum MessageType {
      REQUEST_RES,
      REQUEST_ACK,

      RELEASE_RES,
   }

   private int from;
   private String mid;
   private MessageType mType;
   private String payLoad;
   private long timestamp;

   public Message(int from, String mid, MessageType mType, long timestamp) {
      this.from = from;
      this.mid = mid;
      this.mType = mType;
      this.timestamp = timestamp;
   }

   public Message(int from, String mid, MessageType mType, String payLoad, long timestamp) {
      this.from = from;
      this.mid = mid;
      this.mType = mType;
      this.payLoad = payLoad;
      this.timestamp = timestamp;
   }

   public long getTimestamp() {
      return timestamp;
   }

   public String getMid() {
      return mid;
   }

   public MessageType getmType() {
      return mType;
   }

   public int getFrom() {
      return from;
   }
}
