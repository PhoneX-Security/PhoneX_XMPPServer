package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class SimplePushPart implements PushMessage {
    private String action;
    private long tstamp;
    private JSONObject auxData;

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

    public JSONObject getAuxData() {
        return auxData;
    }

    public void setAuxData(JSONObject auxData) {
        this.auxData = auxData;
    }
}
