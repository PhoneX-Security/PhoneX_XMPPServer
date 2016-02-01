package org.jivesoftware.openfire.plugin.userService.db;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class DbPushDelivery {
    public static final String TABLE_NAME = "ofPushDelivery";
    public static final String FIELD_ID = "dlvrID";
    public static final String FIELD_MSG_ID = "msgId";
    public static final String FIELD_TIME = "dlvrTime";
    public static final String FIELD_USER = "forUser";
    public static final String FIELD_RESOURCE = "forResource";
    public static final String FIELD_STATUS = "dlvrStatus";

    /**
     * Primary key of the delivery recipient.
     */
    private Long id;

    /**
     * Foreign key to the DbPushDelivery object.
     */
    private long pushMessageId;

    /**
     * Recipient of the push message.
     */
    private String user;

    /**
     * Recipient's resource.
     */
    private String resource;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getPushMessageId() {
        return pushMessageId;
    }

    public void setPushMessageId(long pushMessageId) {
        this.pushMessageId = pushMessageId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
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

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }
}
