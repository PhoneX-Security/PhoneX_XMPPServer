package org.jivesoftware.openfire.plugin.userService.push;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class DbPushMessage {
    /**
     * Primary key to the push message record.
     */
    private Integer id;

    /**
     * Push message action name.
     */
    private String action;

    /**
     * Push message creation timestamp.
     */
    private long tstamp;

    /**
     * Designated recipient of this message.
     * Can be JID if is directly aimed to the given user.
     */
    private String toUser;

    /**
     * Aux data to be attached to the notification.
     * Must be in JSON format, will be placed in data:{} field.
     */
    private String auxData;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public long getTstamp() {
        return tstamp;
    }

    public void setTstamp(long tstamp) {
        this.tstamp = tstamp;
    }

    public String getToUser() {
        return toUser;
    }

    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public String getAuxData() {
        return auxData;
    }

    public void setAuxData(String auxData) {
        this.auxData = auxData;
    }
}
