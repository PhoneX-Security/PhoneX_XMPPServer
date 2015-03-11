package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * Envelope for push message.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class SimplePushMessage implements PushMessage {
    private final List<SimplePushPart> parts = new ArrayList<SimplePushPart>();
    private String user;
    private long tstamp;

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = new JSONObject();
        // Base field - action/method of this message.
        obj.put("action", "push");

        // Destination user this push message is designated.
        obj.put("user", user);

        // Time of the event so user can know if he processed it already (perhaps by
        // other means - fetching whole contact list) or not.
        obj.put("tstamp", tstamp);

        // Array of push messages.
        JSONArray msgArray = new JSONArray();
        for (PushMessage part : parts) {
            msgArray.put(part.getJson());
        }

        obj.put("msgs", msgArray);
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

        boolean wasModified = false;

        // Build part list.
        PartRegister myRegister = buildRegister();
        PartRegister foRegister = msg.buildRegister();

        // Iterate my register and add his stuff, if has newer. Delete if present so we can then add all rest.
        Set<String> keys = new HashSet<String>(myRegister.keySet());
        for (String key : keys) {
            final List<SimplePushPart> lst = myRegister.get(key);
            if (lst == null){
                continue;
            }

            // Does this entry exist in ofRegister? if not, continue with merging on different field.
            List<SimplePushPart> foLst = foRegister.get(key);
            if (foLst == null || foLst.isEmpty()){
                foRegister.remove(key);
                continue;
            }

            // Add all elements since our is empty.
            if (lst.isEmpty()){
                lst.addAll(foLst);
                foRegister.remove(key);
                wasModified |= true;
                continue;
            }

            // Our list is non-empty, his as well.
            SimplePushPart myPart = lst.get(0);
            if (myPart.isUnique()){
                // Unique part - take newer.
                SimplePushPart hisPart = foLst.get(0);
                if (myPart.getTstamp() < hisPart.getTstamp()){
                    lst.clear();
                    lst.add(hisPart);
                    wasModified |= true;
                }

                foRegister.remove(key);
                continue;
            } else {
                // Union parts, joint them.
                lst.addAll(foLst);
                foRegister.remove(key);
                wasModified |= true;
                continue;
            }
        }

        // Add all items that left.
        if (!foRegister.isEmpty()) {
            myRegister.putAll(foRegister);
            wasModified |= true;
        }

        // Build new part from the updated myRegister.
        parts.clear();
        for (Map.Entry<String, List<SimplePushPart>> e : myRegister.entrySet()) {
            for (SimplePushPart part : e.getValue()) {
                parts.add(part);
            }
        }

        return wasModified;
    }

    /**
     * Builds mapping for push action -> list of actions.
     * Useful for push message merge.
     *
     * @return
     */
    public PartRegister buildRegister(){
        PartRegister register = new PartRegister();
        for (SimplePushPart part : parts) {
            final String action = part.getAction();

            List<SimplePushPart> lst = register.get(action);
            if (lst == null){
                lst = new ArrayList<SimplePushPart>();
            }

            lst.add(part);
            register.put(action, lst);
        }

        return register;
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

    public static class PartRegister extends HashMap<String, List<SimplePushPart>> {
        // ...
    }
}
