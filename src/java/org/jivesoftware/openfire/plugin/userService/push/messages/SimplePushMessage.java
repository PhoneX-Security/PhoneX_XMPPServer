package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.List;

/**
 * Envelope for push message.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class SimplePushMessage implements PushMessage {
    public static final String FIELD_ACTION = "action";
    public static final String FIELD_USER = "user";
    public static final String FIELD_TIME_STAMP = "tstamp";
    public static final String FIELD_MESSAGES = "msgs";
    public static final String ACTION_PUSH = "push";

    /**
     * All push messages in this push message envelope.
     */
    protected final List<SimplePushPart> parts = new ArrayList<SimplePushPart>();

    /**
     * User this push message is designated to.
     */
    protected String user;

    /**
     * Timestamp of creation of this push message.
     */
    protected long tstamp;

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = new JSONObject();
        // Base field - action/method of this message.
        obj.put(FIELD_ACTION, ACTION_PUSH);

        // Destination user this push message is designated.
        obj.put(FIELD_USER, user);

        // Time of the event so user can know if he processed it already (perhaps by
        // other means - fetching whole contact list) or not.
        obj.put(FIELD_TIME_STAMP, tstamp);

        // Array of push messages.
        JSONArray msgArray = new JSONArray();
        for (PushMessage part : parts) {
            msgArray.put(part.getJson());
        }

        obj.put(FIELD_MESSAGES, msgArray);
        return obj;
    }

    public SimplePushMessage() {

    }

    public SimplePushMessage(String user, long tstamp) {
        this.user = user;
        this.tstamp = tstamp;
    }

    /**
     * Returns TRUE if this message can be merged with given message.
     * Essentially it checks whether username matches (bare JID).
     *
     * @param msg
     * @return
     */
    public boolean canMergeWithMessage(SimplePushMessage msg){
        if (msg == null || msg.getUser() == null){
            throw new RuntimeException("Null sip message encountered");
        }

        JID myJid  = new JID(user);
        JID hisJid = new JID(msg.getUser());
        return myJid.compareTo(hisJid) == 0;
    }

    /**
     * Merges this message with given message into one final message (this one).
     * Final message will contain newest push notifications from unique message type.
     *
     * By merging msg to this message msg can be dropped. If msg is older or contains older information
     * than this message msg is ignored.
     * @param msg
     * @return true if message has been changed.
     */
    public boolean mergeWithMessage(SimplePushMessage msg){
        if (!canMergeWithMessage(msg)){
            throw new RuntimeException("Could not merge with this message - conflicting");
        }

        SimplePushPartRegister myRegister = SimplePushPartRegister.buildFrom(this);
        SimplePushPartRegister foRegister = SimplePushPartRegister.buildFrom(msg);
        boolean wasModified = myRegister.mergeWithRegister(foRegister);
        if (wasModified){
            myRegister.buildParts(parts);
            return true;
        }

        return false;
    }

    public void addPart(SimplePushPart part){
        parts.add(part);
    }

    public void clearParts(){
        parts.clear();
    }

    public List<SimplePushPart> getParts() {
        return parts;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public long getTstamp() {
        return tstamp;
    }

    public void setTstamp(long tstamp) {
        this.tstamp = tstamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimplePushMessage that = (SimplePushMessage) o;

        if (tstamp != that.tstamp) return false;
        if (parts != null ? !parts.equals(that.parts) : that.parts != null) return false;
        if (!user.equals(that.user)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = parts != null ? parts.hashCode() : 0;
        result = 31 * result + user.hashCode();
        result = 31 * result + (int) (tstamp ^ (tstamp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "SimplePushMessage{" +
                "parts=" + parts +
                ", user='" + user + '\'' +
                ", tstamp=" + tstamp +
                '}';
    }
}
