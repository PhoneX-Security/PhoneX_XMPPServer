package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBase;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewAttentionMsg;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple push notification message. Client is asking us to send a destination user simple attention-required message which
 * does not require ACK.
 *
 * Created by dusanklinec on 01.11.15.
 */
public class NewAttentionPush extends PushRequestMessage {
    public static final String ACTION = "newAttention";
    private static final int PRIORITY = 20;

    public NewAttentionPush() {
        init();
    }

    public NewAttentionPush(JSONObject json) throws JSONException {
        super(json);
        init();
    }

    protected void init(){
        priority = PRIORITY;
        requiresAck = false;
    }

    @Override
    public ApnMessageBase getApnMessage(boolean allowGeneric) {
        return new NewAttentionMsg();
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
        return URGENCY_NEW_USER_EVENT;
    }

    @Override
    public boolean isCanUserRequest() {
        return false;
    }

    @Override
    public String getAlertStringKey() {
        return "L_PHX_PUSH_NEW_ATTENTION";
    }
}