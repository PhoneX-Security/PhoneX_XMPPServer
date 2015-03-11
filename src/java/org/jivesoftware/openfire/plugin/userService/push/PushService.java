package org.jivesoftware.openfire.plugin.userService.push;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.push.messages.ClistSyncEventMessage;
import org.jivesoftware.openfire.plugin.userService.push.messages.PushIq;
import org.jivesoftware.openfire.plugin.userService.push.messages.SimplePushMessage;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Push service taking care of the push notifications acknowledged delivery.
 * Created by dusanklinec on 11.03.15.
 */
public class PushService extends IQHandler implements IQResultListener, ServerFeaturesProvider {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int ACK_STATUS_OK   =  1;
    private static final int ACK_STATUS_ERR  = -1;
    private static final int ACK_STATUS_FAIL = -2;

    private        final PriorityBlockingQueue<PushSendRecord> sndQueue = new PriorityBlockingQueue<PushSendRecord>();
    private        final ConcurrentHashMap<String, PushSendRecord> ackWait = new ConcurrentHashMap<String, PushSendRecord>();
    private volatile boolean senderWorking = true;
    private Thread senderThread;

    private UserServicePlugin plugin;
    private PushQueryHandler pushQueryHandler;

    public PushService(UserServicePlugin plugin) {
        super("PushService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);

        // Init push query handler.
        pushQueryHandler = new PushQueryHandler();
        pushQueryHandler.setSvc(this);
        pushQueryHandler.init();

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
        pushQueryHandler.deinit();

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
                    addSendRecord(sndRec, false);
                    break;
                }

                // If sending counter is too high, drop off from the queue.
                if (sndRec.getResendAttempt() > 30){
                    log.info(String.format("Send counter too high for packet %s to %s, dropping", sndRec.getPacketId(), sndRec.getDestination()));

                    // Store delivery result to database so it is not tried to deliver again.
                    persistAck(sndRec, ACK_STATUS_FAIL);
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

    private PushIq buildPushNotification(JID to, SimplePushMessage msg) throws JSONException {
        final PushIq pushNotif = new PushIq();
        pushNotif.setFrom(plugin.getServer().getServerInfo().getXMPPDomain());
        pushNotif.setTo(to);
        pushNotif.setContent(msg);

        return pushNotif;
    }

    private void sendPush(JID to, SimplePushMessage msg) {
        final String domain = plugin.getServer().getServerInfo().getXMPPDomain();

        // Routing table approach.
        try {
            final long curTstamp = System.currentTimeMillis();

            // Store this push message to database for on-connected and on-demand push delivery.
            persistPush(to, msg);

            // The user sent a directed presence to an entity
            // Broadcast it to all connected resources
            // TODO: if user is not connected (no session), postpone this until he connects...
            for (JID jid : plugin.getRoutingTable().getRoutes(to.asBareJID(), new JID(domain))) {
                // Store send requests to the sending queue so it handles re-sends and acknowledgement.
                final PushIq pushNotif = buildPushNotification(jid, msg);
                PushSendRecord sndRec = new PushSendRecord();
                sndRec.setSendTstamp(curTstamp);
                sndRec.setPacket(pushNotif);
                sndRec.setPushMsg(msg);

                // Add record to the queue, possibly removing old ones if desired.
                addSendRecord(sndRec, true);
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
            return null;
        }

        return null;
    }

    @Override
    public IQHandlerInfo getInfo() {
        return PushIq.info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(PushIq.NAMESPACE).iterator();
    }

    /**
     * Check if given record that is to be added cannot be merged with some packet waiting in ackQueue.
     *
     * @param sndRec
     * @return
     */
    private boolean addSendRecordCheckQueue(PushSendRecord sndRec) {
        Enumeration<PushSendRecord> elems = ackWait.elements();
        if (elems == null || !elems.hasMoreElements()){
            return false;
        }

        while(elems.hasMoreElements()){
            PushSendRecord curRec = elems.nextElement();
            if (curRec == null){
                continue;
            }

            synchronized (curRec.lock){
                if (!curRec.canMergeWithRecord(sndRec)){
                    continue;
                }

                // Can merge - do it.
                boolean hasChanged = false;
                try {
                    hasChanged = curRec.mergeWithRecord(sndRec);
                    log.info(String.format("Merged with ackWait item, changed=%s, curRecId=%s", hasChanged, curRec.getPacketId()));

                } catch(Exception e){
                    log.error("Exception in message merge", e);

                    // Cannot proceed, add as a separate message
                    return false;
                }

                // if element has not been changed, ignore this message.
                if (!hasChanged){
                    return true;
                }

                // Element has been changed, set to force re-send.
                curRec.setForceResend(true);
                return true;
            }
        }

        // Default case - no match, add to send queue then.
        return false;
    }

    /**
     * Main entry to submit new push notifications to the queue.
     * Try to merge with existing waiting message to save space / messages / bandwidth.
     * @param sndRec
     * @param tryMerge
     */
    public void addSendRecord(PushSendRecord sndRec, boolean tryMerge) {
        if (!tryMerge){
            sndQueue.add(sndRec);
            return;
        }

        log.info(String.format("Add sendRecord with merge, size: %d, ackSize: %d", sndQueue.size(), ackWait.size()));

        // At first try to observe ack storage if there is some message this new one can be merged with in order not to
        // send excessive amount of messages and to group them together.
        boolean mergedToWaitQueue = addSendRecordCheckQueue(sndRec);
        if (mergedToWaitQueue){
            return;
        }

        // Record to be added to the queue. Either given one or merged one that has been removed before merge.
        PushSendRecord recordToAdd = sndRec;
        Iterator<PushSendRecord> iterator = sndQueue.iterator();
        while(iterator.hasNext()){
            PushSendRecord curRec = iterator.next();
            if (curRec == null){
                continue;
            }

            // If message cannot be merged to the queue, skip it.
            if (!curRec.canMergeWithRecord(sndRec)){
                continue;
            }

            // Remove
            iterator.remove();
            recordToAdd = curRec;

            // Merge curMsg with myMsq,
            boolean wasChanged = false;
            try {
                wasChanged = curRec.mergeWithRecord(sndRec);
                log.info(String.format("MessageMerge, changed=%s", wasChanged));
            } catch(Exception e){
                log.info("Exception in merging objects", e);
                break; // Will be added separately.
            }

            break;
        }

        // Either given record (if it was not possible to merge it with any other record in queue) or new merged record
        // is inserted here.
        sndQueue.add(recordToAdd);
        log.info(String.format("Message enqueued, size=%d", sndQueue.size()));
    }

    /**
     * Persists given simple message with designated recipient.
     * When user connects / asks for recent push notifications, it uses entities stored here.
     * @param to
     * @param msg
     */
    public void persistPush(JID to, SimplePushMessage msg){
        // TODO: implement, onWorkerQueue.
        // TODO: check for uniqueness, if unique, delete old entries...
    }

    /**
     * Stores pushDelivery entity.
     *
     * @param sndRec
     * @param statusCode
     */
    public void persistAck(PushSendRecord sndRec, int statusCode){
        // TODO: implement, onWorkerQueue.
    }

    /**
     * Send recent push notifications for this JID.
     * Called after on-connect or on-demand event. Transfer only non-ACKed push messages.
     * If there are no such messages, return.
     * @param to
     */
    public void sendRecentPushNotifications(JID to){
       // TODO: implement, onWorkerQueue.
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

        // Synchronize on sendRecord so it can be updated while siting in ackWait with new incoming message to re-transmit.
        synchronized (sndRec.lock) {
            // Remove from ack remove.
            ackWait.remove(packetId);
            log.info(String.format("Packet acknowledged, success=%s, storing to db, packetId=%s, from=%s", success, packetId, from));

            // If was updated while waiting in ackQueue, resend again. We cannot persis ACK since this entry
            // passed via merge.
            if (success && sndRec.isForceResend()){
                log.info("Message updated while in ackWait, re-send");
                sndRec.setForceResend(false);
                addSendRecord(sndRec, true);
            } else {
                // Mark this record as finished in database. If success == false, this feature is not yet supported. Store it...
                persistAck(sndRec, success ? ACK_STATUS_OK : ACK_STATUS_ERR);
            }
        }
    }

    /**
     * IQ packet sent by us has timed out.
     * @param packetId
     */
    @Override
    public void answerTimeout(String packetId) {
        PushSendRecord sndRec = ackWait.get(packetId);

        log.info(String.format("Packet timed out: id=%s, sndRec=%s", packetId, sndRec));
        if (sndRec == null) {
            log.info(String.format("Unknown packet received, id=%s, size=%d", packetId, ackWait.size()));
            return;
        }

        // Synchronize on sendRecord so it can be updated while siting in ackWait with new incoming message to re-transmit.
        synchronized (sndRec.lock) {
            // Remove from waiting map.
            ackWait.remove(packetId);

            // Re-schedule sending of this packet.
            // If there is no client session anymore (client offline) this is not reached thus give some
            // reasonable resend boundary, e.g. 10 attempts.
            final int resendAttempts = sndRec.getResendAttempt();
            long timeOffset = 1000;
            if (resendAttempts > 10 && resendAttempts < 20) {
                timeOffset = 5000;
            } else if (resendAttempts > 20) {
                timeOffset = 15000;
            }

            sndRec.setSendTstamp(System.currentTimeMillis() + timeOffset);
            addSendRecord(sndRec, true);
            log.info(String.format("Packet %s re-scheduled with offset %d to %s. ResendAttempt %d", packetId, timeOffset, sndRec.getDestination(), resendAttempts));
        }
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
