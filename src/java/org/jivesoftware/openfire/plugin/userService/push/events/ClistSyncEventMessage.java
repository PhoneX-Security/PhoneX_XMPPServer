package org.jivesoftware.openfire.plugin.userService.push.events;

import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushPart;

/**
 * Push message event signalizing server-stored contactlist has changed and should be reloaded on end devices.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class ClistSyncEventMessage extends SimplePushPart {
    public static final String PUSH = "clistSync";

    public ClistSyncEventMessage() {
        this.setAction(PUSH);
        this.setUnique(true);
    }

    public ClistSyncEventMessage(long tstamp) {
        super(PUSH, tstamp);
        this.setUnique(true);
    }
}
