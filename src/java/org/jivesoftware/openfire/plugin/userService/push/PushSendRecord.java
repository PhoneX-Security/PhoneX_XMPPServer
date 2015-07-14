package org.jivesoftware.openfire.plugin.userService.push;

import org.jivesoftware.openfire.plugin.userService.push.iq.PushIq;
import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushMessage;
import org.json.JSONException;
import org.xmpp.packet.JID;

/**
 * Record for priority queue for failed send requests.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class PushSendRecord implements Comparable<PushSendRecord> {
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
     * Push message to send record - if sending succeeds, this is needed for processing.
     */
    private SimplePushMessage pushMsg;

    /**
     * Actual packet to be sent.
     */
    private PushIq packet;

    /**
     * If true, this message is designated to be forcefully resent even if user acknowledged its success recipient.
     */
    private boolean forceResend = false;

    /**
     * Simple locking object for synchronization.
     */
    public final Object lock = new Object();

    @Override
    public int compareTo(PushSendRecord pushSendRecord) {
        if (pushSendRecord == null){
            return -1;
        }

        // Tie-breaker is the packet ID.
        if (sendTstamp == pushSendRecord.getSendTstamp()){
            return packet.getID().compareTo(pushSendRecord.getPacketId());
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
    public boolean canMergeWithRecord(PushSendRecord rec){
        if (rec == null || rec.getPushMsg() == null){
            throw new RuntimeException("Null message encountered");
        }

        return pushMsg.canMergeWithMessage(rec.getPushMsg());
    }

    /**
     * Merges this record with given one so this record is most up-to-date.
     * @param rec
     * @return
     */
    public boolean mergeWithRecord(PushSendRecord rec) throws JSONException {
        if (!canMergeWithRecord(rec)) {
            throw new RuntimeException("Record cannot be merged with given one!");
        }

        // Merge messages at first.
        boolean wasChanged = pushMsg.mergeWithMessage(rec.getPushMsg());

        // If was not changed, it has no point to update it...
        if (!wasChanged){
            return false;
        }

        // Regenerate new PushIq packet so it contains fresh payload.
        rebuildPacket(true);
        resendAttempt = 0;
        if (sendTstamp > rec.getSendTstamp()){
            sendTstamp = rec.getSendTstamp();
        }

        return true;
    }

    /**
     * Rebuilds IQ packet from pushMessage. Used if pushMessage has been changed and packet needs to be rebuilt to be up to date.
     * This is used in queue mechanism, if newer push request in enqueued, it cancels all previous push requests of the
     * same type. These parts are removed from push message.
     * @param newId
     */
    public void rebuildPacket(boolean newId) throws JSONException {
        final PushIq newPacket = new PushIq();
        newPacket.setTo(packet.getTo());
        newPacket.setFrom(packet.getFrom());
        newPacket.setType(packet.getType());
        if (!newId){
            newPacket.setID(packet.getID());
        }

        // Set new content - builds child element.
        newPacket.setContent(pushMsg);
        packet = newPacket;
    }

    @Override
    public String toString() {
        return "PushSendRecord{" +
                "sendTstamp=" + sendTstamp +
                ", lastSendTstamp=" + lastSendTstamp +
                ", resendAttempt=" + resendAttempt +
                ", pushMsg=" + pushMsg +
                ", packet=" + packet +
                ", forceResend=" + forceResend +
                '}';
    }

    public long getSendTstamp() {
        return sendTstamp;
    }

    public void setSendTstamp(long sendTstamp) {
        this.sendTstamp = sendTstamp;
    }

    public String getPacketId() {
        return packet.getID();
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

    public JID getDestination() {
        return packet.getTo();
    }

    public SimplePushMessage getPushMsg() {
        return pushMsg;
    }

    public void setPushMsg(SimplePushMessage pushMsg) throws JSONException {
        this.pushMsg = pushMsg;
    }

    public PushIq getPacket() {
        return packet;
    }

    public void setPacket(PushIq packet) {
        this.packet = packet;
    }

    public boolean isForceResend() {
        return forceResend;
    }

    public void setForceResend(boolean forceResend) {
        this.forceResend = forceResend;
    }
}
