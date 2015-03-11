package org.jivesoftware.openfire.plugin.userService.push;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by dusanklinec on 11.03.15.
 */
public interface PushMessage {
    public JSONObject getJson() throws JSONException;
}
