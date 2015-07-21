package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmpp.packet.JID;

import javax.naming.OperationNotSupportedException;

/**
 * Created by dusanklinec on 17.07.15.
 */
public class ApnMessageBase implements ApnMessage {
    protected String action;

    /**
     * Identifier of the push message request.
     * Generated by client.
     * Used for duplicates avoidance.
     * Client can identify this message in order to cancel its delivery to the target device, used in calls.
     */
    protected String key;

    /**
     * Expiration time of the message in milliseconds.
     * This message should be considered as expired if given amount of time passes from its reception.
     * Mainly designed for active push messages such as call requests.
     */
    protected Long expiration;

    /**
     * Timestamp of the creation.
     */
    protected long timestamp;

    /**
     * Badge number for this notifications (how many notifications waiting from the same action).
     */
    protected int badge = 0;

    @Override
    public JSONObject apnToJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("p", getAction());
        root.put("t", getTimestamp());
        root.put("b", getBadge());

        final String akey = getKey();
        if (akey != null && !akey.isEmpty()){
            root.put("k", akey);
        }

        final Long aexp = getExpiration();
        if (aexp != null && aexp > 0){
            root.put("e", aexp);
        }

        return root;
    }

    public String getAction() {
        return action;
    }

    public ApnMessage setAction(String action) {
        this.action = action;
        return this;
    }

    public String getKey() {
        return key;
    }

    public ApnMessage setKey(String key) {
        this.key = key;
        return this;
    }

    public Long getExpiration() {
        return expiration;
    }

    public ApnMessage setExpiration(Long expiration) {
        this.expiration = expiration;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ApnMessage setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public int getBadge() {
        return badge;
    }

    public ApnMessage setBadge(int badge) {
        this.badge = badge;
        return this;
    }
}
