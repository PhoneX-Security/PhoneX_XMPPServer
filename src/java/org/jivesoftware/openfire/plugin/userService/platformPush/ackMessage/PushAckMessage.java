package org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage;

import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.db.DbPushMessage;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequestMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmpp.packet.JID;

/**
 * Push ACK message.
 * Created by dusanklinec on 14.07.15.
 */
public class PushAckMessage {
    public static final String FIELD_ACTION = "p";
    public static final String FIELD_KEY = "k";
    public static final String FIELD_TIME_STAMP = "t";

    protected String action;

    /**
     * Identifier of the push message request.
     * Can be used to delete given message. Timestamp is not considered if key is given.
     */
    protected String key;

    /**
     * Timestamp of the push message to cancel.
     */
    protected Long timestamp;

    protected PushRequestMessage msgRef;
    protected DbPlatformPush pushRef;

    public PushAckMessage() {
    }

    public PushAckMessage(JSONObject json) throws JSONException {
        parserFromJson(json);
    }

    public static String parseAction(JSONObject json) throws JSONException {
        return json.has(FIELD_ACTION) ? json.getString(FIELD_ACTION) : null;
    }

    /**
     * Tries to load message from JSON object.
     * @param json
     */
    public void parserFromJson(JSONObject json) throws JSONException {
        if (json == null){
            return;
        }

        if (json.has(FIELD_ACTION)){
            action = json.getString(FIELD_ACTION);
        }

        if (json.has(FIELD_KEY)){
            key = json.getString(FIELD_KEY);
        }

        if (json.has(FIELD_TIME_STAMP)){
            timestamp = json.getLong(FIELD_TIME_STAMP);
        }
    }

    @Override
    public String toString() {
        return "PushAckMessage{" +
                "action='" + action + '\'' +
                ", key='" + key + '\'' +
                ", timestamp=" + timestamp +
                ", msgRef=" + msgRef +
                ", pushRef=" + pushRef +
                '}';
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public PushRequestMessage getMsgRef() {
        return msgRef;
    }

    public void setMsgRef(PushRequestMessage msgRef) {
        this.msgRef = msgRef;
    }

    public DbPlatformPush getPushRef() {
        return pushRef;
    }

    public void setPushRef(DbPlatformPush pushRef) {
        this.pushRef = pushRef;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
