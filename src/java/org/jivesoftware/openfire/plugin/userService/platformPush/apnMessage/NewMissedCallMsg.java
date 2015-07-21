package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * Created by dusanklinec on 17.07.15.
 */
public class NewMissedCallMsg extends ApnMessageBase implements ApnMessage {
    @Override
    public String getAction() {
        return "mcall";
    }
}
