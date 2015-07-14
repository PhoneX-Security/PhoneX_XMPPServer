package org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage;

import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dusanklinec on 14.07.15.
 */
public class PushRequest {
    protected JID fromUser;
    protected long tstamp;
    protected List<PushRequestMessage> messages;

    public boolean addMessage(PushRequestMessage msg){
        if (messages == null){
            messages = new ArrayList<PushRequestMessage>();
        }

        return messages.add(msg);
    }

    public JID getFromUser() {
        return fromUser;
    }

    public void setFromUser(JID fromUser) {
        this.fromUser = fromUser;
    }

    public long getTstamp() {
        return tstamp;
    }

    public void setTstamp(long tstamp) {
        this.tstamp = tstamp;
    }

    public List<PushRequestMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<PushRequestMessage> messages) {
        this.messages = messages;
    }
}
