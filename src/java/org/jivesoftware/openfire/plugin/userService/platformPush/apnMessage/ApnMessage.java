package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by dusanklinec on 17.07.15.
 */
public interface ApnMessage {

    /**
     * Converts to the JSON object.
     * @return
     */
    JSONObject apnToJson() throws JSONException;

    String getAction();
    ApnMessage setAction(String action);

    String getKey();
    ApnMessage setKey(String key);

    Long getExpiration();
    ApnMessage setExpiration(Long expiration);

    long getTimestamp();
    ApnMessage setTimestamp(long timestamp);

    int getBadge();
    ApnMessage setBadge(int badge);
}
