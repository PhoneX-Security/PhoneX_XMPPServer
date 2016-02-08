package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBase;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewMissedCallOfflineMsg;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * New missed call notification sent to the user, offline notification. Action triggered by message server (silo).
 * Created by dusanklinec on 04.02.16.
 */
public class NewMissedCallOfflinePush  extends PushRequestMessage {
    public static final String ACTION = "newMissedCallOffline";

    public NewMissedCallOfflinePush() {
    }

    public NewMissedCallOfflinePush(JSONObject json) throws JSONException {
        super(json);
    }

    @Override
    public ApnMessageBase getApnMessage(boolean allowGeneric) {
        return new NewMissedCallOfflineMsg();
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
        return "L_PHX_PUSH_NEW_MISSED_CALL_OFFLINE";
    }
}