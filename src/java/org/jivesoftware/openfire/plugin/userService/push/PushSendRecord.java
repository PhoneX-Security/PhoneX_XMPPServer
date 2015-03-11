package org.jivesoftware.openfire.plugin.userService.push;

import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushMessage;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.Comparator;

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
    private IQ packet;

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

    public void incSendCtr(){
        resendAttempt += 1;
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

    public void setPushMsg(SimplePushMessage pushMsg) {
        this.pushMsg = pushMsg;
    }

    public IQ getPacket() {
        return packet;
    }

    public void setPacket(IQ packet) {
        this.packet = packet;
    }
}
