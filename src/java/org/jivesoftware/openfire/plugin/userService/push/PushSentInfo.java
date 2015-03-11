package org.jivesoftware.openfire.plugin.userService.push;

import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushMessage;
import org.xmpp.packet.JID;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class PushSentInfo {
    private String packetId;
    private SimplePushMessage push;
    private JID dest;

    public PushSentInfo() {

    }

    public PushSentInfo(String packetId, JID dest) {
        this.packetId = packetId;
        this.dest = dest;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public JID getDest() {
        return dest;
    }

    public void setDest(JID dest) {
        this.dest = dest;
    }

    public SimplePushMessage getPush() {
        return push;
    }

    public void setPush(SimplePushMessage push) {
        this.push = push;
    }
}
