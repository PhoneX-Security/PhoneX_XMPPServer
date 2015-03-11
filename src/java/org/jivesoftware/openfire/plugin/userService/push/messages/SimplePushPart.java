package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Abstract class represents a single push part. For code simplicity each push part has own object.
 * Created by dusanklinec on 11.03.15.
 */
public abstract class SimplePushPart implements PushMessage {
    private String action;
    private long tstamp;
    private JSONObject auxData;

    /**
     * If TRUE then this push notification is of unique type, thus only one (and newer) is accepted in the push message.
     * If is FALSE then multiple of this messages can be in push message.
     */
    private boolean unique = true;

    public SimplePushPart() {
    }

    public SimplePushPart(String action, long tstamp) {
        this.action = action;
        this.tstamp = tstamp;
    }

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("push", action);
        obj.put("tstamp", tstamp);
        if (auxData != null){
            obj.put("data", auxData);
        }

        return obj;
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

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public JSONObject getAuxData() {
        return auxData;
    }

    public void setAuxData(JSONObject auxData) {
        this.auxData = auxData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimplePushPart that = (SimplePushPart) o;

        if (tstamp != that.tstamp) return false;
        if (unique != that.unique) return false;
        if (!action.equals(that.action)) return false;
        if (auxData != null ? !auxData.equals(that.auxData) : that.auxData != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + (int) (tstamp ^ (tstamp >>> 32));
        result = 31 * result + (auxData != null ? auxData.hashCode() : 0);
        result = 31 * result + (unique ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SimplePushPart{" +
                "action='" + action + '\'' +
                ", tstamp=" + tstamp +
                ", auxData=" + auxData +
                ", unique=" + unique +
                '}';
    }
}
