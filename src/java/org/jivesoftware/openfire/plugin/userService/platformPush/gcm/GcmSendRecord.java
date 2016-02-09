package org.jivesoftware.openfire.plugin.userService.platformPush.gcm;

import com.google.android.gcm.server.Message;
import org.jivesoftware.openfire.plugin.userService.platformPush.ApnPushBuilder;
import org.jivesoftware.openfire.plugin.userService.push.iq.PushIq;
import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushMessage;
import org.json.JSONException;
import org.xmpp.packet.JID;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Record for priority queue for failed send requests.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class GcmSendRecord implements Comparable<GcmSendRecord> {
    public static final int MAX_RETRIES = 30;

    /**
     * Packet ID static counter - generator.
     */
    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Scheduled time for next sending.
     */
    private long sendTstamp       = 0;

    /**
     * Time of last send attempt.
     */
    private long lastSendTstamp   = 0;

    /**
     * Number of recent resend attempts.
     */
    private int  resendAttempt    = 0;

    /**
     * Initial backoff.
     */
    private int backoff = GcmSender.BACKOFF_INITIAL_DELAY;

    /**
     * Push message to send record - if sending succeeds, this is needed for processing.
     */
    private Message pushMsg;

    /**
     * Destination of the packet (token)
     */
    private String to;

    /**
     * If true, this message is designated to be forcefully resent even if user acknowledged its success recipient.
     */
    private boolean forceResend = false;

    /**
     * Maximum number of retries for this particular message.
     */
    private int maxRetries = MAX_RETRIES;

    /**
     * Packet ID for further reference (logs).
     */
    private String packetId;

    /**
     * Builder used to build the message, contains source DB messages contained in this object, used for
     * tracking sending state. Helps to delete non-ACK message on successful send attempt.
     */
    private ApnPushBuilder builder;

    /**
     * Simple locking object for synchronization.
     */
    public final Object lock = new Object();

    public GcmSendRecord() {
        Random random = new Random();
        packetId = String.valueOf(random.nextInt(1000) + "-" + counter.incrementAndGet());
    }

    @Override
    public int compareTo(GcmSendRecord pushSendRecord) {
        if (pushSendRecord == null){
            return -1;
        }

        // Tie-breaker is the packet ID.
        if (sendTstamp == pushSendRecord.getSendTstamp()){
            if (pushMsg == null){
                return -1;
            } else if (pushSendRecord.getPushMsg() == null){
                return -1;
            }

            return pushMsg.toString().compareTo(pushSendRecord.getPushMsg().toString());
        }

        return sendTstamp < pushSendRecord.getSendTstamp() ? -1 : 1;
    }

    /**
     * Increment resend attempt counter.
     */
    public void incSendCtr(){
        resendAttempt += 1;
    }

    /**
     * Return true if this record can be merged with given one.
     * @param rec
     * @return
     */
    public boolean canMergeWithRecord(GcmSendRecord rec){
        if (rec == null || rec.getPushMsg() == null){
            throw new RuntimeException("Null message encountered");
        }

        return false;
    }

    /**
     * Merges this record with given one so this record is most up-to-date.
     * @param rec
     * @return
     */
    public boolean mergeWithRecord(GcmSendRecord rec) throws JSONException {
        if (!canMergeWithRecord(rec)) {
            throw new RuntimeException("Record cannot be merged with given one!");
        }

        return false;
    }

    @Override
    public String toString() {
        return "GcmSendRecord{" +
                "sendTstamp=" + sendTstamp +
                ", lastSendTstamp=" + lastSendTstamp +
                ", resendAttempt=" + resendAttempt +
                ", pushMsg=" + pushMsg +
                ", to='" + to + '\'' +
                ", forceResend=" + forceResend +
                ", lock=" + lock +
                '}';
    }

    public long getSendTstamp() {
        return sendTstamp;
    }

    public void setSendTstamp(long sendTstamp) {
        this.sendTstamp = sendTstamp;
    }

    public long getLastSendTstamp() {
        return lastSendTstamp;
    }

    public void setLastSendTstamp(long lastSendTstamp) {
        this.lastSendTstamp = lastSendTstamp;
    }

    public int getResendAttempt() {
        return resendAttempt;
    }

    public void setResendAttempt(int resendAttempt) {
        this.resendAttempt = resendAttempt;
    }

    public Message getPushMsg() {
        return pushMsg;
    }

    public void setPushMsg(Message pushMsg) {
        this.pushMsg = pushMsg;
    }

    public boolean isForceResend() {
        return forceResend;
    }

    public void setForceResend(boolean forceResend) {
        this.forceResend = forceResend;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public int getBackoff() {
        return backoff;
    }

    public void setBackoff(int backoff) {
        this.backoff = backoff;
    }

    public ApnPushBuilder getBuilder() {
        return builder;
    }

    public void setBuilder(ApnPushBuilder builder) {
        this.builder = builder;
    }
}
