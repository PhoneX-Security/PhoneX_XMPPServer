package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * Created by dusanklinec on 04.02.16.
 */
public class NewMissedCallOfflineMsg extends ApnMessageBase implements ApnMessage {
    public static final String ACTION = "mcallof";

    @Override
    public String getAction() {
        return ACTION;
    }
}
