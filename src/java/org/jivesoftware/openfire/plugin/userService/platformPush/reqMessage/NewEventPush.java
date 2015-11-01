package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple push notification message. Client is asking us to send a destination user simple new event message which
 * does not require ACK.
 *
 * Created by dusanklinec on 01.11.15.
 */
public class NewEventPush  extends PushRequestMessage {
    public static final String ACTION = "newEvent";

    public NewEventPush() {
        requiresAck = false;
    }

    public NewEventPush(JSONObject json) throws JSONException {
        super(json);
    }

    @Override
    public String getAction() {
        return ACTION;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public int getUrgencyType() {
        return URGENCY_REALTIME;
    }

    @Override
    public boolean requiresAck() {
        return false;
    }

    @Override
    public String getAlertString() {
        return "L_PHX_PUSH_NEW_EVENT";
    }
}
