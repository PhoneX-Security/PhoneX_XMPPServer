package org.jivesoftware.openfire.plugin.userService.push;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.push.messages.ClistSyncEventMessage;
import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushMessage;
import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushPart;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Push service taking care of the push notifications acknowledged delivery.
 * Created by dusanklinec on 11.03.15.
 */
public class PushService extends IQHandler implements IQResultListener, ServerFeaturesProvider {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    public  static final String ELEMENT_NAME = "push";
    public  static final String NAMESPACE = "urn:xmpp:phx";
    private        final IQHandlerInfo info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);
    private        final PriorityBlockingQueue<PushSendRecord> sndQueue = new PriorityBlockingQueue<PushSendRecord>();

    private UserServicePlugin plugin;

    public PushService(UserServicePlugin plugin) {
        super("PushService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);
    }

    public void deinit(){
        // Remove this as IQ listener.
        try {
            IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            iqRouter.removeHandler(this);
        } catch (Exception ex) {
            log.error("Could not unregister from IQ router", ex);
        }
    }

    public void pushClistSync(String user) throws JSONException {
        JID to = new JID(user);

        // Build push action.
        SimplePushMessage msg = buildClistSyncNotification(to.toBareJID());
        List<PushSentInfo> pushSentInfo = this.sendPush(to, msg);
        // TODO: process what succeded and what not...
    }

    public SimplePushMessage buildClistSyncNotification(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();

        SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(new ClistSyncEventMessage(tstamp));
        return msg;
    }

    private IQ buildPushNotification(JID to, String json){
        final IQ pushNotif = new IQ(IQ.Type.get);

        Element pushElem = pushNotif.setChildElement(ELEMENT_NAME, NAMESPACE);
        pushElem.addAttribute("version", "1");

        Element jsonElement = pushElem.addElement("json");
        jsonElement.addCDATA(json);

        pushNotif.setFrom(plugin.getServer().getServerInfo().getXMPPDomain());
        pushNotif.setTo(to);
        return pushNotif;
    }

    private List<PushSentInfo> sendPush(JID to, SimplePushMessage msg) {
        final IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        final String domain     = plugin.getServer().getServerInfo().getXMPPDomain();
        final List<PushSentInfo> info = new ArrayList<PushSentInfo>(4);

        // Routing table approach.
        try {
            final String json = msg.getJson().toString();

            // The user sent a directed presence to an entity
            // Broadcast it to all connected resources
            // TODO: if user is not connected (no session), postpone this until he connects...
            for (JID jid : plugin.getRoutingTable().getRoutes(to.asBareJID(), new JID(domain))) {
                final IQ pushNotif = buildPushNotification(to, json);

                // Send with IQ router.
                iqRouter.addIQResultListener(pushNotif.getID(), this, 1000 * 60);
                iqRouter.route(pushNotif);

                log.info(String.format("Routing packet to: %s, packetId=%s", jid, pushNotif.getID()));
                plugin.getRoutingTable().routePacket(jid, pushNotif, false);

                info.add(new PushSentInfo(pushNotif.getID(), jid));
            }
        } catch (Exception e) {
            // Well we just don't care then.
            log.error("Exception in routingTable send", e);
        }

        return info;
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        final IQ.Type iqType = packet.getType();
        log.info(String.format("Handle IQ packet_type: %s", iqType));
        if (IQ.Type.result.equals(iqType)) {
            // TODO: mark given push update
            return null;
        }

        return null;
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(NAMESPACE).iterator();
    }

    @Override
    public void receivedAnswer(IQ packet) {
        final String packetId = packet.getID();
        final JID from = packet.getFrom();
        final IQ.Type type = packet.getType();
        log.info(String.format("Packet received: id=%s, from=%s, packet=%s", packetId, from, packet));
        // TODO: check if response is not an error. In that case mark push notification
        // as failed since client probably does not support this option.
        // ...
        if (IQ.Type.error.equals(type)) {
            log.info("Remote party could not process this message.");
            // TODO: mark finished for this id.
            return;
        }

        // TODO implement success result.
    }

    @Override
    public void answerTimeout(String packetId) {
        // TODO: is active?
        log.info(String.format("Packet timed out: id=%s", packetId));
    }

    public UserServicePlugin getPlugin() {
        return plugin;
    }

    public void setPlugin(UserServicePlugin plugin) {
        this.plugin = plugin;
    }
}
