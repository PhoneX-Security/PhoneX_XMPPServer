package org.jivesoftware.openfire.plugin.userService.platformPush;

import org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage.PushAck;
import org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage.PushAckMessage;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.*;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

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

        }  else if (NewAttentionPush.ACTION.equals(action)){
            pm = new NewAttentionPush();

        }  else if (NewEventPush.ACTION.equals(action)){
            pm = new NewEventPush();

        }  else if (NewOfflineMsgPush.ACTION.equals(action)){
            pm = new NewOfflineMsgPush();

        }  else if (NewMessageOfflinePush.ACTION.equals(action)){
            pm = new NewMessageOfflinePush();

        }  else if (NewMissedCallOfflinePush.ACTION.equals(action)){
            pm = new NewMissedCallOfflinePush();

        } else if (generic) {
            pm = new PushRequestMessage();

        }

        return pm;
    }

    /**
     * Creates a new instance of the message by its APN action.
     * @param action
     * @param generic
     * @return
     */
    public static PushRequestMessage getMessageByApnAction(String action, boolean generic){
        PushRequestMessage pm = null;
        if (NewMessageMsg.ACTION.equals(action)){
            pm = new NewMessagePush();

        } else if (NewCallMsg.ACTION.equals(action)){
            pm = new NewActiveCallPush();

        }  else if (NewMissedCallMsg.ACTION.equals(action)){
            pm = new NewMissedCallPush();

        } else if (NewAttentionMsg.ACTION.equals(action)){
            pm = new NewAttentionPush();

        } else if (NewEventMsg.ACTION.equals(action)){
            pm = new NewEventPush();

        } else if (NewOfflineMsg.ACTION.equals(action)){
            pm = new NewOfflineMsgPush();

        } else if (NewMessageOfflineMsg.ACTION.equals(action)){
            pm = new NewMessageOfflinePush();

        } else if (NewMissedCallOfflineMsg.ACTION.equals(action)){
            pm = new NewMissedCallOfflinePush();

        } else if (generic) {
            pm = new PushRequestMessage();

        }

        return pm;
    }

    /**
     * APN action parsing from json object
     * @param json
     * @return
     * @throws JSONException
     */
    public static String parseApnAction(JSONObject json) throws JSONException {
        return json.has(PushAckMessage.FIELD_ACTION) ? json.getString(PushAckMessage.FIELD_ACTION) : null;
    }

    /**
     * Process JSON push message request.
     * @param json
     * @return
     */
    public PushRequest processPushRequest(JSONObject json, IQ packet){
        if (packet == null){
            return null;
        }

        return processPushRequest(json, packet.getFrom());
    }

    /**
     * Process JSON push message request.
     * @param json
     * @return
     */
    public PushRequest processPushRequest(JSONObject json, JID fromUser){
        if (json == null || fromUser == null){
            return null;
        }

        // Parse request body.
        try {
            PushRequest preq = new PushRequest();
            preq.setFromUser(fromUser);
            preq.setTstamp(System.currentTimeMillis());
            if (!json.has("pushreq")){
                return preq;
            }

            final JSONArray reqs = json.getJSONArray("pushreq");
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

    /**
     * Process JSON encoded push message ACK from the client.
     * This message is sent by device which received Apple Push Notification to let server know
     * the message was received and should not be sent again in the next Apple Push Notification bulk.
     *
     * @param json
     * @param packet
     * @return
     */
    public PushAck processPushAck(JSONObject json, IQ packet){
        if (json == null || packet == null){
            return null;
        }

        // Parse request body.
        try {
            PushAck pack = new PushAck();
            pack.setFromUser(packet.getFrom());
            pack.setTstamp(System.currentTimeMillis());

            JSONArray reqs = json.getJSONArray("acks");
            final int numReq = reqs.length();
            for(int i=0; i<numReq; i++){
                JSONObject curReq = reqs.getJSONObject(i);

                final String action = parseApnAction(curReq);
                if (action == null || action.isEmpty()){
                    log.warn(String.format("Message action not recognized %s", curReq));
                    continue;
                }

                PushRequestMessage pm = getMessageByApnAction(action, false);
                if (pm == null) {
                    log.warn(String.format("Unrecognized push message: %s", action));
                    continue;
                }

                PushAckMessage curAck = new PushAckMessage(curReq);
                curAck.setMsgRef(pm);
                pack.addMessage(curAck);
            }

            return pack;
        } catch(JSONException e){
            log.warn("Error in parsing request body", e);
            return null;
        }
    }
}
