package org.jivesoftware.openfire.plugin.userService.push;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class DbPushDelivery {
    /**
     * Primary key of the delivery recipient.
     */
    private Integer id;

    /**
     * Foreign key to the DbPushDelivery object.
     */
    private int pushMessageId;

    /**
     * JID in full form of the recipient of the push message.
     */
    private String jid;

    /**
     * Recipient timestamp - when client acknowledged this message.
     */
    private long tstamp;

    /**
     * ACK status of the message.
     *  0 = OK
     * -1 = ERROR
     */
    private int status;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getPushMessageId() {
        return pushMessageId;
    }

    public void setPushMessageId(int pushMessageId) {
        this.pushMessageId = pushMessageId;
    }

    public String getJid() {
        return jid;
    }

    public void setJid(String jid) {
        this.jid = jid;
    }

    public long getTstamp() {
        return tstamp;
    }

    public void setTstamp(long tstamp) {
        this.tstamp = tstamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
