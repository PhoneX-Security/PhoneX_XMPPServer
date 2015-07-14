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
public class PushRequestParser {
    private static final Logger log = LoggerFactory.getLogger(PushRequestParser.class);

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

                PushRequestMessage pm = null;
                if (NewMessagePush.ACTION.equals(action)){
                    pm = new NewMessagePush(curReq);
                } else if (NewMissedCallPush.ACTION.equals(action)){
                    pm = new NewMissedCallPush(curReq);
                } else if (NewActiveCallPush.ACTION.equals(action)){
                    pm = new NewActiveCallPush(curReq);
                } else if (CancelActiveCallPush.ACTION.equals(action)){
                    pm = new CancelActiveCallPush(curReq);
                } else {
                    log.warn(String.format("Unrecognized push message: %s", action));
                    continue;
                }

                preq.addMessage(pm);
            }

            return preq;
        } catch(JSONException e){
            log.warn("Error in parsing request body", e);
            return null;
        }
    }
}
