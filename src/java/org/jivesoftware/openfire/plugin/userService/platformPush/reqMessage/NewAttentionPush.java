package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

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

    public NewAttentionPush() {
        requiresAck = false;
    }

    public NewAttentionPush(JSONObject json) throws JSONException {
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
        return URGENCY_NEW_USER_EVENT;
    }

    @Override
    public String getAlertStringKey() {
        return "L_PHX_PUSH_NEW_ATTENTION";
    }
}