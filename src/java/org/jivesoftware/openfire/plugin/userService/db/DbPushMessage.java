package org.jivesoftware.openfire.plugin.userService.db;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class DbPushMessage {
    public static final String TABLE_NAME = "ofPushMessages";
    public static final String FIELD_ID = "msgId";
    public static final String FIELD_ACTION = "msgAction";
    public static final String FIELD_TIME = "msgTime";
    public static final String FIELD_EXPIRE_TIME = "msgExpire";
    public static final String FIELD_USER = "forUser";
    public static final String FIELD_RESOURCE = "forResource";
    public static final String FIELD_IS_DURABLE = "isDurable";
    public static final String FIELD_IS_UNIQUE = "isUnique";
    public static final String FIELD_DATA = "auxData";
    public static final String FIELD_AUX1 = "aux1";
    public static final String FIELD_AUX2 = "aux2";

    /**
     * Primary key to the push message record.
     */
    private Long id;

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
     * Resource
     */
    private String toResource;

    /**
     * Aux data to be attached to the notification.
     * Must be in JSON format, will be placed in data:{} field.
     */
    private String auxData;

    /**
     * Aux data attribute for message.
     */
    private String aux1;

    /**
     * Aux data attribute for message.
     */
    private String aux2;

    /**
     * Should this message resist server restart?
     */
    private boolean durable;

    /**
     * If TRUE then this push notification is of unique type, thus only one (and newer) is accepted in the push message.
     * If is FALSE then multiple of this messages can be in push message.
     */
    private boolean unique;

    /**
     * Timestamp expiration, if needed.
     */
    private Long expireTstamp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public Long getExpireTstamp() {
        return expireTstamp;
    }

    public void setExpireTstamp(Long expireTstamp) {
        this.expireTstamp = expireTstamp;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getToResource() {
        return toResource;
    }

    public void setToResource(String toResource) {
        this.toResource = toResource;
    }

    public String getAux1() {
        return aux1;
    }

    public void setAux1(String aux1) {
        this.aux1 = aux1;
    }

    public String getAux2() {
        return aux2;
    }

    public void setAux2(String aux2) {
        this.aux2 = aux2;
    }
}
