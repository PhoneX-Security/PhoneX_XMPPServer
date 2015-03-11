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
import java.util.concurrent.ConcurrentHashMap;
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
    private        final ConcurrentHashMap<String, PushSendRecord> ackWait = new ConcurrentHashMap<String, PushSendRecord>();
    private volatile boolean senderWorking = true;
    private Thread senderThread;

    private UserServicePlugin plugin;

    public PushService(UserServicePlugin plugin) {
        super("PushService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);

        // Sender thread.
        senderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runSenderThread();
            }
        });

        senderThread.setName("PushSender");
        senderThread.start();
    }

    public void deinit(){
        // Shutdown message sender.
        senderWorking = false;

        // Remove this as IQ listener.
        try {
            IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            iqRouter.removeHandler(this);
        } catch (Exception ex) {
            log.error("Could not unregister from IQ router", ex);
        }
    }

    public void runSenderThread(){
        log.info("Sender thread started.");
        final IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();

        while(senderWorking){
            while(!sndQueue.isEmpty()){
                PushSendRecord sndRec = sndQueue.poll();
                if (sndRec == null){
                    continue;
                }

                final long curTime = System.currentTimeMillis();

                // Compare time of sending with current time. If we have some time, take some nap.
                if ((curTime - sndRec.getSendTstamp()) < 0){
                    // Add back to queue and take a short nap.
                    sndQueue.add(sndRec);
                    break;
                }

                // If sending counter is too high, drop off from the queue.
                if (sndRec.getResendAttempt() > 30){
                    log.info(String.format("Send counter too high for packet %s to %s, dropping", sndRec.getPacketId(), sndRec.getDestination()));

                    // TODO: store delivery result to database so it is not tried to deliver again.
                    // ...

                    continue;
                }

                // Send
                try {
                    // Is there still valid route to given destination?
                    boolean hasClientRoute = plugin.getRoutingTable().hasClientRoute(sndRec.getDestination());
                    if (!hasClientRoute){
                        log.info(String.format("Client route disappeared meanwhile. Dropping request for id %s user %s", sndRec.getPacketId(), sndRec.getDestination()));
                        continue;
                    }

                    sndRec.incSendCtr();
                    iqRouter.addIQResultListener(sndRec.getPacketId(), this, 1000 * 30);
                    iqRouter.route(sndRec.getPacket());

                    log.info(String.format("Routing packet to: %s, packetId=%s", sndRec.getDestination(), sndRec.getPacketId()));
                    plugin.getRoutingTable().routePacket(sndRec.getDestination(), sndRec.getPacket(), true);
                    sndRec.setLastSendTstamp(curTime);

                    // Store this record to the waiting map where it waits for ack or for timeout.
                    ackWait.put(sndRec.getPacketId(), sndRec);
                    log.info(String.format("Packet sent, ackWaitSize: %d", ackWait.size()));
                } catch(Exception ex){
                    log.error("Error during sending a packet", ex);
                }
            }

            try {
                Thread.sleep(150);
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }

        log.info("Sender thread finishing.");
    }

    public void pushClistSync(String user) throws JSONException {
        JID to = new JID(user);

        // Build push action.
        SimplePushMessage msg = buildClistSyncNotification(to.toBareJID());
        this.sendPush(to, msg);
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

    private void sendPush(JID to, SimplePushMessage msg) {
        final IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        final String domain     = plugin.getServer().getServerInfo().getXMPPDomain();
        final List<PushSentInfo> info = new ArrayList<PushSentInfo>(4);

        // Routing table approach.
        try {
            final String json = msg.getJson().toString();
            final long curTstamp = System.currentTimeMillis();

            // The user sent a directed presence to an entity
            // Broadcast it to all connected resources
            // TODO: if user is not connected (no session), postpone this until he connects...
            // TODO: store this push message to database.
            for (JID jid : plugin.getRoutingTable().getRoutes(to.asBareJID(), new JID(domain))) {
                // Store send requests to the sending queue so it handles re-sends and acknowledgement.
                final IQ pushNotif = buildPushNotification(jid, json);
                PushSendRecord sndRec = new PushSendRecord();
                sndRec.setSendTstamp(curTstamp);
                sndRec.setPacket(pushNotif);
                sndRec.setPushMsg(msg);
                sndQueue.add(sndRec);
            }
        } catch (Exception e) {
            // Well we just don't care then.
            log.error("Exception in routingTable send", e);
        }
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

    /**
     * Process IQ result/error for sent packet.
     *
     * @param packet
     */
    @Override
    public void receivedAnswer(IQ packet) {
        final String packetId = packet.getID();
        final JID from = packet.getFrom();
        final IQ.Type type = packet.getType();
        log.info(String.format("Packet received: id=%s, from=%s, packet=%s", packetId, from, packet));

        boolean success = !IQ.Type.error.equals(type);
        PushSendRecord sndRec = ackWait.get(packetId);
        if (sndRec == null){
            log.info(String.format("Unknown packet received, id=%s, from=%s", packetId, from));
            return;
        }

        log.info(String.format("Packet acknowledged, success=%s, storing to db, packetId=%s, from=%s", success, packetId, from));
        // TODO: mark this record as finished in database. If success == false, this feature is not yet supported. Store it...
        // ...

    }

    /**
     * IQ packet sent by us has timed out.
     * @param packetId
     */
    @Override
    public void answerTimeout(String packetId) {
        PushSendRecord sndRec = ackWait.get(packetId);
        log.info(String.format("Packet timed out: id=%s, sndRec=%s", packetId, sndRec));
        if (sndRec == null){
            log.info(String.format("Unknown packet received, id=%s, size=%d", packetId, ackWait.size()));
            return;
        }

        // Remove from waiting map.
        ackWait.remove(packetId);

        // Re-schedule sending of this packet.
        // If there is no client session anymore (client offline) this is not reached thus give some
        // reasonable resend boundary, e.g. 10 attempts.
        final int resendAttempts = sndRec.getResendAttempt();
        long timeOffset = 1000;
        if (resendAttempts > 10 && resendAttempts < 20){
            timeOffset = 5000;
        } else if (resendAttempts > 20){
            timeOffset = 15000;
        }

        sndRec.setSendTstamp(System.currentTimeMillis() + timeOffset);
        sndQueue.add(sndRec);
    }

    public UserServicePlugin getPlugin() {
        return plugin;
    }

    public void setPlugin(UserServicePlugin plugin) {
        this.plugin = plugin;
    }

    public ConcurrentHashMap<String, PushSendRecord> getAckWait() {
        return ackWait;
    }

    public PriorityBlockingQueue<PushSendRecord> getSndQueue() {
        return sndQueue;
    }
}
