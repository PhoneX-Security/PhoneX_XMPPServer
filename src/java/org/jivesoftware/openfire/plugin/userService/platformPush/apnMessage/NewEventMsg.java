package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * New event message, no-ACK message. Unspecified event happened.
 *
 * Created by dusanklinec on 01.11.15.
 */
public class NewEventMsg  extends ApnMessageBase implements ApnMessage {
    @Override
    public String getAction() {
        return "nevt";
    }
}
