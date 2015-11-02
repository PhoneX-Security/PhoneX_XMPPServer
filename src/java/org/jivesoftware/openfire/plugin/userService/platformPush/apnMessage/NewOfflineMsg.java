package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * Created by dusanklinec on 02.11.15.
 */
public class NewOfflineMsg extends ApnMessageBase implements ApnMessage {
    public static final String ACTION = "noff";

    @Override
    public String getAction() {
        return ACTION;
    }
}
