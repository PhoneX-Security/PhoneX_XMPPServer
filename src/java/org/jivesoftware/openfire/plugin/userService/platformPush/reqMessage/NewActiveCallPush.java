package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBase;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewCallMsg;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push request sent by client to notify a destination user he has incoming call.
 *
 * Created by dusanklinec on 14.07.15.
 */
public class NewActiveCallPush extends PushRequestMessage {
    public static final String ACTION = "newCall";

    public NewActiveCallPush() {
    }

    public NewActiveCallPush(JSONObject json) throws JSONException {
        super(json);
    }

    @Override
    public ApnMessageBase getApnMessage(boolean allowGeneric) {
        return new NewCallMsg();
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
        return true;
    }

    @Override
    public String getAlertStringKey() {
        return "L_PHX_PUSH_NEW_CALL";
    }
}
