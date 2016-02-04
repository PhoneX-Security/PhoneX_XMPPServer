package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * New message sent to the user, offline notification. Action triggered by message server (silo).
 * Created by dusanklinec on 04.02.16.
 */
public class NewMessageOfflinePush  extends PushRequestMessage {
    public static final String ACTION = "newMessageOffline";

    public NewMessageOfflinePush() {
    }

    public NewMessageOfflinePush(JSONObject json) throws JSONException {
        super(json);
    }

    @Override
    public String getAction() {
        return ACTION;
    }

    @Override
    public boolean isUnique() {
        return false;
    }

    @Override
    public int getUrgencyType() {
        return URGENCY_NEW_USER_EVENT;
    }

    @Override
    public boolean requiresAck() {
        return true;
    }

    @Override
    public String getAlertStringKey() {
        return "L_PHX_PUSH_NEW_MESSAGE_OFFLINE";
    }
}
