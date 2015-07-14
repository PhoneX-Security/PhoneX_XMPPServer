package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
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
        return true;
    }
}
