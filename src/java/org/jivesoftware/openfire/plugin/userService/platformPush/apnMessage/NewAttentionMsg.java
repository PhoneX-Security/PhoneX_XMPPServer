package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

/**
 * Attention-required message. User is asked to start the application for some reason.
 *
 * Created by dusanklinec on 01.11.15.
 */
public class NewAttentionMsg extends ApnMessageBase implements ApnMessage {
    public static final String ACTION = "natt";

    @Override
    public String getAction() {
        return ACTION;
    }
}
