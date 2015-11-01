package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * Attention-required message. User is asked to start the application for some reason.
 *
 * Created by dusanklinec on 01.11.15.
 */
public class NewAttentionMsg extends ApnMessageBase implements ApnMessage {
    @Override
    public String getAction() {
        return "natt";
    }
}
