package org.jivesoftware.openfire.plugin.userService.push;

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

    private String packetId;
    private JID destination;

    @Override
    public int compareTo(PushSendRecord pushSendRecord) {
        if (pushSendRecord == null){
            return -1;
        }

        // Tie-breaker is the packet ID.
        if (sendTstamp == pushSendRecord.getSendTstamp()){
            return packetId.compareTo(pushSendRecord.getPacketId());
        }

        return sendTstamp < pushSendRecord.getSendTstamp() ? -1 : 1;
    }

    public long getSendTstamp() {
        return sendTstamp;
    }

    public void setSendTstamp(long sendTstamp) {
        this.sendTstamp = sendTstamp;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
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
        return destination;
    }

    public void setDestination(JID destination) {
        this.destination = destination;
    }
}
