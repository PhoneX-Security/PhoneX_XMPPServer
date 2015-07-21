package org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage;

import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequestMessage;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.List;

/**
 * Push message ACK sent by client. Client indicates that he received given push messages.
 *
 * Created by dusanklinec on 14.07.15.
 */
public class PushAck {
    protected JID fromUser;
    protected long tstamp;
    protected List<PushAckMessage> messages;

    public boolean addMessage(PushAckMessage msg){
        if (messages == null){
            messages = new ArrayList<PushAckMessage>();
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

    public List<PushAckMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<PushAckMessage> messages) {
        this.messages = messages;
    }
}
