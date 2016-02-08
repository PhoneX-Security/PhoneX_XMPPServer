package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBase;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewMessageMsg;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push request sent by client to notify a destination user he has a new message.
 *
 * Created by dusanklinec on 14.07.15.
 */
public class NewMessagePush extends PushRequestMessage {
    public static final String ACTION = "newMessage";
    private static final int PRIORITY = 110;

    public NewMessagePush() {
        init();
    }

    public NewMessagePush(JSONObject json) throws JSONException {
        super(json);
        init();
    }

    protected void init(){
        priority = PRIORITY;
    }

    @Override
    public ApnMessageBase getApnMessage(boolean allowGeneric) {
        return new NewMessageMsg();
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
        return "L_PHX_PUSH_NEW_MESSAGE";
    }

    @Override
    public String getAlertSummaryKey() {
        return "L_PHX_PUSH_SUM_NEW_MESSAGE";
    }
}
