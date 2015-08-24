package org.jivesoftware.openfire.plugin.userService.push.events;

import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushPart;

/**
 * Pairing request database has to be checked.
 * Created by dusanklinec on 18.08.15.
 */
public class PairingRequestCheckEventMessage extends SimplePushPart {
    public static final String PUSH = "pair";

    public PairingRequestCheckEventMessage() {
        this.setAction(PUSH);
        this.setUnique(true);
    }

    public PairingRequestCheckEventMessage(long tstamp) {
        super(PUSH, tstamp);
        this.setUnique(true);
    }
}
