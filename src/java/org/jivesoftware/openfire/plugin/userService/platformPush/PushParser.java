package org.jivesoftware.openfire.plugin.userService.platformPush;

import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

/**
 * Simple parser for JSON encoded push message requests for APN.
 * Request is sent from device to the XMPP server in order to request Apple push notification delivery to another device.
 *
 * Created by dusanklinec on 14.07.15.
 */
public class PushParser {
    private static final Logger log = LoggerFactory.getLogger(PushParser.class);

    /**
     * Creates a new instance of the message by its action.
     * @param action action string identifying message object.
     * @param generic if false and message is not recognized, null is returned. If true and message is not recognized,
     *                general message is used.
     * @return PushRequestMessage or child of PushRequestMessage.
     */
    public static PushRequestMessage getMessageByAction(String action, boolean generic){
        PushRequestMessage pm = null;
        if (NewMessagePush.ACTION.equals(action)){
            pm = new NewMessagePush();
        } else if (NewMissedCallPush.ACTION.equals(action)){
            pm = new NewMissedCallPush();
        } else if (NewActiveCallPush.ACTION.equals(action)){
            pm = new NewActiveCallPush();
        } else if (generic) {
            pm = new PushRequestMessage();
        }

        return pm;
    }

    /**
     * Process JSON push message request.
     * @param json
     * @return
     */
    public PushRequest process(JSONObject json, IQ packet){
        if (json == null || packet == null){
            return null;
        }

        // Parse request body.
        try {
            PushRequest preq = new PushRequest();
            preq.setFromUser(packet.getFrom());
            preq.setTstamp(System.currentTimeMillis());

            JSONArray reqs = json.getJSONArray("pushreq");
            final int numReq = reqs.length();
            for(int i=0; i<numReq; i++){
                JSONObject curReq = reqs.getJSONObject(i);
                final String action = PushRequestMessage.parseAction(curReq);
                if (action == null || action.isEmpty()){
                    continue;
                }

                PushRequestMessage pm = getMessageByAction(action, false);
                if (pm == null) {
                    log.warn(String.format("Unrecognized push message: %s", action));
                    continue;
                }

                pm.parserFromJson(curReq);
                preq.addMessage(pm);
            }

            return preq;
        } catch(JSONException e){
            log.warn("Error in parsing request body", e);
            return null;
        }
    }
}
