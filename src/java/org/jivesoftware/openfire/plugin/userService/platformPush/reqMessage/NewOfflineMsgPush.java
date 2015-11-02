package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push request sent by server to notify a destination user he has a new offline message.
 *
 * Created by dusanklinec on 02.11.15.
 */
public class NewOfflineMsgPush extends PushRequestMessage {
    public static final String ACTION = "newOfflineMsg";

    public NewOfflineMsgPush() {
    }

    public NewOfflineMsgPush(JSONObject json) throws JSONException {
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
    public boolean isCanUserRequest() {
        return false;
    }

    @Override
    public String getAlertStringKey() {
        return "L_PHX_PUSH_NEW_OFFLINE_MESSAGE";
    }
}
