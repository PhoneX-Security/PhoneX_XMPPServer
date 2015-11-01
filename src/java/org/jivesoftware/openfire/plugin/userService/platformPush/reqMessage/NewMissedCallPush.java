package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push request sent by client to notify a destination user he has a new missed call.
 *
 * Created by dusanklinec on 14.07.15.
 */
public class NewMissedCallPush extends PushRequestMessage {
    public static final String ACTION = "newMissedCall";

    public NewMissedCallPush() {
    }

    public NewMissedCallPush(JSONObject json) throws JSONException {
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
        return "L_PHX_PUSH_NEW_MISSED_CALL";
    }
}
