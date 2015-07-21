package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * Created by dusanklinec on 17.07.15.
 */
public class NewCallMsg extends ApnMessageBase implements ApnMessage {
    @Override
    public String getAction() {
        return "ncall";
    }
}
