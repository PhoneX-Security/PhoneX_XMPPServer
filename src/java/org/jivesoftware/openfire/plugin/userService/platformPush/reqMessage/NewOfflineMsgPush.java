package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBase;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewOfflineMsg;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push request sent by server to notify a destination user he has a new offline message.
 *
 * Created by dusanklinec on 02.11.15.
 */
public class NewOfflineMsgPush extends PushRequestMessage {
    public static final String ACTION = "newOfflineMsg";
    public static final String FIELD_MESSAGE_TYPE = "mtype";
    private static final int PRIORITY = 50;

    protected String offlineMessageType;

    public NewOfflineMsgPush() {
        init();
    }

    public NewOfflineMsgPush(JSONObject json) throws JSONException {
        super(json);
        init();
    }

    protected void init(){
        priority = PRIORITY;
    }

    @Override
    public ApnMessageBase getApnMessage(boolean allowGeneric) {
        return new NewOfflineMsg();
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

    @Override
    public String getAlertSummaryKey() {
        return "L_PHX_PUSH_SUM_NEW_OFFLINE_MESSAGE";
    }

    @Override
    public void parserFromJson(JSONObject json) throws JSONException {
        super.parserFromJson(json);

        if (json.has(FIELD_MESSAGE_TYPE)){
            setOfflineMessageType(json.getString(FIELD_MESSAGE_TYPE));
        }
    }

    public String getOfflineMessageType() {
        return offlineMessageType;
    }

    public void setOfflineMessageType(String messageType) {
        this.offlineMessageType = messageType;
        this.aux1 = messageType;
    }

    @Override
    public String toString() {
        return "NewOfflineMsgPush{" +
                "offlineMessageType='" + offlineMessageType + '\'' +
                "} " + super.toString();
    }
}
