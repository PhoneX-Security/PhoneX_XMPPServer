package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
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
    public String getAction() {
        return ACTION;
    }

    @Override
    public boolean isUnique() {
        return true;
    }
}
