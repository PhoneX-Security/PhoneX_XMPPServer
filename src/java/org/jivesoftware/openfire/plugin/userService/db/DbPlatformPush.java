package org.jivesoftware.openfire.plugin.userService.db;

/**
 * Database record for platform push message (Apple Push Notifications).
 * Created by dusanklinec on 14.07.15.
 */
public class DbPlatformPush {
    public static final String FIELD_ID = "ofMsgId";
    public static final String FIELD_KEY = "ofMsgKey";
    public static final String FIELD_ACTION = "ofMsgAction";
    public static final String FIELD_TIME = "ofMsgTime";
    public static final String FIELD_EXPIRE = "ofMsgExpire";
    public static final String FIELD_FOR_USER = "ofForUser";
    public static final String FIELD_FOR_RESOURCE = "ofForResource";
    public static final String FIELD_FROM_USER = "ofFromUser";
    public static final String FIELD_FROM_RESOURCE = "ofFromResource";
    public static final String FIELD_TYPE = "ofMsgType";
    public static final String FIELD_DURABLE = "ofIsDurable";
    public static final String FIELD_UNIQUE = "ofIsUnique";
    public static final String FIELD_AUX1 = "ofAux1";
    public static final String FIELD_AUX2 = "ofAux2";
    public static final String FIELD_AUX_DATA = "ofAuxData";

    protected Long id;
    protected String key;
    protected String action;
    protected long time;
    protected Long expiration;
    protected String forUser;
    protected String forResource;
    protected String fromUser;
    protected String fromResource;
    protected int type=0;
    protected boolean durable;
    protected boolean unique;
    protected String aux1;
    protected String aux2;
    protected String auxData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    public String getForUser() {
        return forUser;
    }

    public void setForUser(String forUser) {
        this.forUser = forUser;
    }

    public String getForResource() {
        return forResource;
    }

    public void setForResource(String forResource) {
        this.forResource = forResource;
    }

    public String getFromUser() {
        return fromUser;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public String getFromResource() {
        return fromResource;
    }

    public void setFromResource(String fromResource) {
        this.fromResource = fromResource;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
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

    public String getAuxData() {
        return auxData;
    }

    public void setAuxData(String auxData) {
        this.auxData = auxData;
    }
}
