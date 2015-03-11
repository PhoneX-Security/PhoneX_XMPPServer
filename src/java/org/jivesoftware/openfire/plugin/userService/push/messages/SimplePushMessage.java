package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class SimplePushMessage implements PushMessage {
    private final List<PushMessage> parts = new ArrayList<PushMessage>();
    private String user;
    private long tstamp;

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = new JSONObject();
        // Base field - action/method of this message.
        obj.put("action", "push");

        // Destination user this push message is designated.
        obj.put("user", user);

        // Time of the event so user can know if he processed it already (perhaps by
        // other means - fetching whole contact list) or not.
        obj.put("tstamp", tstamp);

        // Array of push messages.
        JSONArray msgArray = new JSONArray();
        for (PushMessage part : parts) {
            msgArray.put(part.getJson());
        }

        obj.put("msgs", msgArray);
        return obj;
    }

    public SimplePushMessage() {

    }

    public SimplePushMessage(String user, long tstamp) {
        this.user = user;
        this.tstamp = tstamp;
    }

    public void addPart(PushMessage part){
        parts.add(part);
    }

    public void clearParts(){
        parts.clear();
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
}
