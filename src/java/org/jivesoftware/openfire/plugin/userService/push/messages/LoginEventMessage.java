package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push message event signalizing user has logged in with certificate with given not before time.
 * Usage: notify old logged in devices that user with newer certificate has logged in so they can log out.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class LoginEventMessage extends SimplePushPart {
    public static final String PUSH = "login";
    private long certNotBefore;

    public LoginEventMessage() {
        setAction(PUSH);
    }

    public LoginEventMessage(long tstamp, long certNotBefore) {
        super(PUSH, tstamp);
        this.certNotBefore = certNotBefore;
    }

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("certNotBefore", certNotBefore);
        setAuxData(obj);

        return super.getJson();
    }

    public long getCertNotBefore() {
        return certNotBefore;
    }

    public void setCertNotBefore(long certNotBefore) {
        this.certNotBefore = certNotBefore;
    }
}
