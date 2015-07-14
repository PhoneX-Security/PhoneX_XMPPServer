package org.jivesoftware.openfire.plugin.userService.platformPush;

import org.dom4j.Element;
import org.eclipse.jetty.util.ajax.JSON;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.clientState.iq.ActiveIq;
import org.jivesoftware.openfire.plugin.userService.db.DbEntityManager;
import org.jivesoftware.openfire.plugin.userService.platformPush.iq.PushMessageAckIq;
import org.jivesoftware.openfire.plugin.userService.platformPush.iq.PushMessageReqIq;
import org.jivesoftware.openfire.plugin.userService.platformPush.iq.PushTokenUpdateIq;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequest;
import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.jivesoftware.openfire.plugin.userService.utils.LRUCache;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.Iterator;

/**
 * Handles IQ related to the Apple push notifications registration.
 * Created by dusanklinec on 03.07.15.
 */
public class PlatformPushHandler extends IQHandler implements ServerFeaturesProvider {
    private static final Logger log = LoggerFactory.getLogger(PlatformPushHandler.class);

    private UserServicePlugin plugin;
    private final LRUCache<JID, TokenConfig> tokenCache = new LRUCache<JID, TokenConfig>(128);

    public PlatformPushHandler(UserServicePlugin plugin) {
        super("ClientStateService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);
        tokenCache.clear();
    }

    public void deinit(){
        tokenCache.clear();

        // Remove this as IQ listener.
        try {
            IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            iqRouter.removeHandler(this);
        } catch (Exception ex) {
            log.error("Could not unregister from IQ router", ex);
        }
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        final IQ.Type iqType = packet.getType();
        log.info(String.format("Handle IQ packet_type: %s", iqType));
        if (IQ.Type.result.equals(iqType)) {
            return null;
        }

        // TODO: verify from field matches the session the packet belongs to.
        final JID from = packet.getFrom();
        final String userName = from.asBareJID().toString();
        final Element elem = packet.getChildElement();
        if (elem == null){
            return null;
        }

        final String tagName = elem.getName();
        if (PushMessageAckIq.ELEMENT_NAME.equals(tagName)){
            log.info("push ack tag");
            return handlePushMessageAck(packet, elem);

        } else if (PushMessageReqIq.ELEMENT_NAME.equals(tagName)){
            log.info("push request");
            return handlePushMessageReq(packet, elem);

        } else if (PushTokenUpdateIq.ELEMENT_NAME.equals(tagName)){
            log.info("push token update tag");
            return handlePushTokenUpdate(packet, elem);

        }

        return null;
    }

    /**
     * Entry point for handling platform push message acknowledgements.
     * JSON packet has the following format:
     *
     * {"acks":[
     *  {"push":"newMsg", "tstamp":1435928809, "msgid":1221},
     *  {"push":"newMissedCall", "tstamp":1435928800, "msgid":1222}
     * ]}
     *
     * @param packet
     * @param elem
     * @return
     */
    public IQ handlePushMessageAck(IQ packet, Element elem){
        final IQ retPacket = IQ.createResultIQ(packet);
        JSONObject json = null;
        try {
            json = getJsonFromIQ(packet);
        } catch (JSONException e) {
            log.warn("Error parsing JSON from the packet");
            return retPacket;
        }

        if (json == null){
            return retPacket;
        }

        // TODO: process ACK.

        return retPacket;
    }

    /**
     * Entry point for handling platform push message requests.
     * JSON packet has the following format:
     *
     * {"pushreq":[
     *  {"push":"newMessage", "target": "test-internal3@phone-x.net"},
     *  {"push":"newMissedCall", "target": "test-internal3@phone-x.net"},
     *  {"push":"newCall", "key":"af45bed", "expire":180000, "target": "test-internal3@phone-x.net"}
     * ]}
     *
     * Or equivalently for canceling new call request
     * {"pushreq":[
     *  {"push":"newCallCancel", "key":"af45bed"}
     * ]}
     *
     * @param packet
     * @param elem
     * @return
     */
    public IQ handlePushMessageReq(IQ packet, Element elem){
        final IQ retPacket = IQ.createResultIQ(packet);
        JSONObject json = null;
        try {
            json = getJsonFromIQ(packet);
        } catch (JSONException e) {
            log.warn("Error parsing JSON from the packet", e);
            return retPacket;
        }

        PushRequestParser parser = new PushRequestParser();
        try {
            final PushRequest request = parser.process(json, packet);
            if (request == null){
                log.warn("Error in parsing push request");
                return retPacket;
            }



            // TODO: persist all messages

            // TODO: trigger sending all unacknowledged push messages to the client via APN.
            // ...

        } catch(Exception e){
            log.warn("Error in parsing request body", e);
            return retPacket;
        }

        return retPacket;
    }

    /**
     * Entry point for handling platform push configuration updates.
     * Json structure of the configuration packet:
     *
     * {"platform":"ios", "token":"0f34819381000405",
     *  "version":"1", "app_version":"1.2.0", "os_version":"8.4",
     *  "langs":["cs", "en"],
     *  "debug":1
     * }
     *
     * Key "platform" is mandatory. Currently only ios platform is recognized. For ios platform also "token" is mandatory.
     *
     * @param packet
     * @param elem
     * @return
     */
    public IQ handlePushTokenUpdate(IQ packet, Element elem){
        final IQ retPacket = IQ.createResultIQ(packet);
        JSONObject json = null;
        try {
            json = getJsonFromIQ(packet);
            if (json == null){
                return retPacket;
            }

            final JID from = packet.getFrom();

            // Build token config from the JSON object.
            final TokenConfig tokenConfig = TokenConfig.buildFromJson(json);
            if (tokenConfig == null){
                log.info("Could not build token from the IQ");
                return retPacket;
            }

            tokenConfig.setUser(from);
            log.info(String.format("Token configuration received from %s, token: %s", from, tokenConfig));

            // Get token from the cache, if already have this token, avoid database manipulation.
            final TokenConfig tokenFromCache = tokenCache.get(from);
            if (tokenFromCache != null && tokenFromCache.equals(tokenConfig)){
                log.debug(String.format("Token update ignored, same in cache for user: %s", from));
                return retPacket;
            }

            // Store to the database. Update if the JID is same.
            DbEntityManager.persistAppleTokenConfig(tokenConfig);
            // Store to the LRU cache for fast lookup.
            tokenCache.put(from, tokenConfig);

            // TODO: notify push manager we have a new valid token for use, old messages might be dumped to the contact.
            // TODO: we have a new valid token -> reflect this in the last active record.
            // ...

        } catch (JSONException e) {
            log.warn("Error parsing JSON from the packet");
            return retPacket;
        }

        return retPacket;
    }

    /**
     * Extracts JSON object from the IQ packet that has form
     * <iq xmlns="jabber:client" type="get" id="193-393472" from="phone-x.net" to="test-internal3@phone-x.net/2e6bb33">
     *     <push xmlns="urn:xmpp:phx" version="1"><json>{"usr":"test-internal3@phone-x.net"}</json></push>
     * </iq>
     * @return
     */
    public JSONObject getJsonFromIQ(IQ packet) throws JSONException {
        final String JSON_FIELD = "json";
        final Element elem = packet.getChildElement();
        if (elem == null){
            return null;
        }

        // Element containing JSON code.
        Element jsonElement = null;

        // Procedure that looks through child elements and find the one with name "json".
        final Iterator it = elem.elementIterator();
        while(it.hasNext()){
            final Object obj = it.next();
            if (!(obj instanceof Element)){
                continue;
            }

            final Element subElem = (Element) obj;
            if (JSON_FIELD.equalsIgnoreCase(subElem.getName())){
                jsonElement = subElem;
                break;
            }
        }

        // No such JSON element was found.
        if (jsonElement == null){
            return null;
        }

        final String jsonString = jsonElement.getText();
        return new JSONObject(jsonString);
    }

    @Override
    public IQHandlerInfo getInfo() {
        return PushTokenUpdateIq.info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(PushTokenUpdateIq.NAMESPACE).iterator();
    }

    public UserServicePlugin getPlugin() {
        return plugin;
    }
}
