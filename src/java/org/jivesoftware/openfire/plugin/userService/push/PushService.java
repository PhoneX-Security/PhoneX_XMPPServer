package org.jivesoftware.openfire.plugin.userService.push;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.push.messages.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Push service taking care of the push notifications acknowledged delivery.
 * Created by dusanklinec on 11.03.15.
 */
public class PushService extends IQHandler implements IQResultListener, ServerFeaturesProvider {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    public static final int ACK_STATUS_OK   =  1;
    public static final int ACK_STATUS_ERR  = -1;
    public static final int ACK_STATUS_FAIL = -2;

    private        final PriorityBlockingQueue<PushSendRecord> sndQueue = new PriorityBlockingQueue<PushSendRecord>();
    private        final ConcurrentHashMap<String, PushSendRecord> ackWait = new ConcurrentHashMap<String, PushSendRecord>();
    private        PushSender sender;
    private        PushExecutor executor;

    private UserServicePlugin plugin;
    private PushQueryHandler pushQueryHandler;
    private PresenceQueryHandler presenceQueryHandler;

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

        // Init presence query handler.
        presenceQueryHandler = new PresenceQueryHandler();
        presenceQueryHandler.setSvc(this);
        presenceQueryHandler.init();

        // Sender thread.
        sender = new PushSender(this);
        sender.start();

        // Executor thread.
        executor = new PushExecutor(this);
        executor.start();
    }

    public void deinit(){
        sender.deinit();
        executor.deinit();
        pushQueryHandler.deinit();
        presenceQueryHandler.deinit();

        // Remove this as IQ listener.
        try {
            IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            iqRouter.removeHandler(this);
        } catch (Exception ex) {
            log.error("Could not unregister from IQ router", ex);
        }
    }

    /**
     * Entry point for new incoming push message sent to XMPP queue.
     * @param obj
     */
    public void handlePushRequestFromQueue(JSONObject obj) {
        try {
            final String userName = obj.getString("user");
            final JSONArray msgs = obj.getJSONArray("msgs");
            final int msgsCnt = msgs.length();
            for(int i=0; i < msgsCnt; i++){
                JSONObject msg = msgs.getJSONObject(i);
                final String pushAction = msg.getString("push");
                log.info("Push notification for: " + userName + "; msg=" + pushAction + ";");

                if (ClistSyncEventMessage.PUSH.equalsIgnoreCase(pushAction)) {
                    log.info("ClistSync request for user: " + userName);
                    pushClistSync(userName, obj, msg);

                } else if (NewCertEventMessage.PUSH.equalsIgnoreCase(pushAction)) {
                    log.info("Login event registered.");
                    pushNewCertEvent(userName, obj, msg);

                } else if (AuthCheckEventMessage.PUSH.equalsIgnoreCase(pushAction)){
                    log.info("AuthCheck request event registered.");

                } else if (ContactRequestEventMessage.PUSH.equalsIgnoreCase(pushAction)){
                    log.info("Contact request event registered.");

                } else if (LicenseCheckEventMessage.PUSH.equalsIgnoreCase(pushAction)){
                    log.info("license check request event registered.");

                } else if (MissedCallEventMessage.PUSH.equalsIgnoreCase(pushAction)){
                    log.info("missed call event registered.");

                } else if (NewFileEventMessage.PUSH.equalsIgnoreCase(pushAction)){
                    log.info("new file event registered.");

                } else if (VersionCheckEventMessage.PUSH.equalsIgnoreCase(pushAction)){
                    log.info("new version event registered.");

                } else {
                    log.info(String.format("Unknown push event: %s", pushAction));
                }
            }
        } catch(Exception e){
            log.error("Exception in push request from queue handling", e);
        }
    }

    /**
     * Entry point for push message. This method gets called when plugin receives message to push clistSync message
     * to designated user.
     * @param user
     * @throws JSONException
     */
    public void pushClistSync(String user, JSONObject obj, JSONObject msg) throws JSONException {
        final JID to = new JID(user);
        final Long tstamp = !msg.has("tstamp") ? System.currentTimeMillis() : msg.getLong("tstamp");

        // Build push action.
        SimplePushMessage msgx = new SimplePushMessage(to.toBareJID(), tstamp);
        final ClistSyncEventMessage evt = new ClistSyncEventMessage(tstamp);

        // Delete all previous & older notifications and insert this new one.
        final DbPushMessage dbMsg = evt.toDbEntity();
        dbMsg.setToUser(to.toBareJID());
        dbMsg.setToResource(to.getResource());

        Long id = DbEntityManager.persistDbMessage(dbMsg, true);
        evt.setMessageId(id);

        msgx.addPart(evt);
        this.sendPush(to, msgx);
    }

    /**
     * Entry point for push message. This method gets called when plugin receives message to push login event message
     * to designated user.
     * @param obj
     */
    public void pushNewCertEvent(String user, JSONObject obj, JSONObject msg) throws JSONException {
        final JID to = new JID(user);
        final Long tstamp = !msg.has("tstamp") ? System.currentTimeMillis() : msg.getLong("tstamp");
        if (!msg.has("data")){
            log.info("Push does not contain data.");
            return;
        }

        final JSONObject data = msg.getJSONObject("data");
        if (!data.has(NewCertEventMessage.FIELD_NOT_BEFORE)){
            log.info("NotBefore is missing in push req");
            return;
        }

        final long certNotBefore = data.getLong(NewCertEventMessage.FIELD_NOT_BEFORE);
        final String certHasPrefix = data.has(NewCertEventMessage.FIELD_CERT_HASH_PREFIX) ? data.getString(NewCertEventMessage.FIELD_CERT_HASH_PREFIX) : null;
        log.info("new cert push detected: " + obj.toString());

        NewCertEventMessage evt = new NewCertEventMessage(tstamp, certNotBefore, certHasPrefix);
        SimplePushMessage msgx = new SimplePushMessage(to.toBareJID(), tstamp);

        // Check database for newest certificate record.
        // If newest is present in database, this one we drop.
        List<Long> idsToDelete = new ArrayList<Long>();
        Collection<DbPushMessage> msgsInDb = DbEntityManager.getPushByUserAndAction(to.toBareJID(), evt.getAction());

        for (DbPushMessage dbPushMessage : msgsInDb) {

            // Get NotBefore of the database record.
            final String strNotBefore = dbPushMessage.getAux1();
            Long notBefore = null;
            try {
                notBefore = Long.parseLong(strNotBefore);
            } catch(Exception e){
                log.error("Cannot parse not before aux", e);
            }

            // If cannot be parsed, delete this record.
            if (notBefore == null){
                idsToDelete.add(dbPushMessage.getId());
                continue;
            }

            // If is smaller, keep the maximal time record. Given one is obsolete.
            if (certNotBefore <= notBefore){
                log.info(String.format("Not before is smaller than in db record; %d vs %d, dbid %d",
                        certNotBefore, notBefore, dbPushMessage.getId())
                );
                return;
            }

            // Stored one is smaller. Add to delete.
            idsToDelete.add(dbPushMessage.getId());
        }

        // Delete marked messages.
        DbEntityManager.deleteMessages(idsToDelete);

        // Store given message to database.
        DbPushMessage dbMsg = evt.toDbEntity();
        dbMsg.setToUser(to.toBareJID());
        dbMsg.setToResource(to.getResource());
        final Long id = DbEntityManager.persistDbMessage(dbMsg, false);

        evt.setMessageId(id);
        msgx.addPart(evt);

        // Send to currently logged in users.
        this.sendPush(to, msgx);
    }

    /**
     * Builds wrapping IQ packet for push message.
     * @param to
     * @param msg
     * @return
     * @throws JSONException
     */
    private PushIq buildPushNotification(JID to, SimplePushMessage msg) throws JSONException {
        final PushIq pushNotif = new PushIq();
        pushNotif.setFrom(plugin.getServer().getServerInfo().getXMPPDomain());
        pushNotif.setTo(to);
        pushNotif.setContent(msg);

        return pushNotif;
    }

    /**
     * Sends given push message to all routes associated with JID (enqueues for sending).
     * @param to
     * @param msg
     */
    private void sendPush(JID to, SimplePushMessage msg) {
        final String domain = plugin.getServer().getServerInfo().getXMPPDomain();

        // Routing table approach.
        try {
            final long curTstamp = System.currentTimeMillis();

            // The user sent a directed presence to an entity
            // Broadcast it to all connected resources
            int sendCtr = 0;
            for (JID jid : plugin.getRoutingTable().getRoutes(to.asBareJID(), new JID(domain))) {
                // Store send requests to the sending queue so it handles re-sends and acknowledgement.
                final PushIq pushNotif = buildPushNotification(jid, msg);
                PushSendRecord sndRec = new PushSendRecord();
                sndRec.setSendTstamp(curTstamp);
                sndRec.setPacket(pushNotif);
                sndRec.setPushMsg(msg);

                // Add record to the queue, possibly removing old ones if desired.
                addSendRecord(sndRec, true);
                sendCtr += 1;
            }

            log.info(String.format("Sending to: %s, number of routes: %d", to, sendCtr));
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

        final Element elem = packet.getChildElement();
        if (elem == null){
            return null;
        }

        final String tagName = elem.getName();
        if (PushIq.ELEMENT_NAME.equals(tagName)){
            log.info("push tag");
        } else if (PushQueryIq.ELEMENT_NAME.equals(tagName)){
            return pushQueryHandler.handleIQ(packet);
        } else if (PresenceQueryIq.ELEMENT_NAME.equals(tagName)){
            return presenceQueryHandler.handleIQ(packet);
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
     * Stores pushDelivery entity.
     *
     * @param sndRec
     * @param statusCode
     */
    public void persistAck(PushSendRecord sndRec, int statusCode){
        final SimplePushMessage pushMsg = sndRec == null ? null : sndRec.getPushMsg();
        if (sndRec == null || pushMsg == null || pushMsg.getParts() == null){
            log.error("Invalid send record received");
            return;
        }

        // For each part separate ack.
        for (SimplePushPart part : pushMsg.getParts()) {
            final Long msgId = part.getMessageId();
            if (msgId == null){
                continue;
            }

            DbPushDelivery dbAck = new DbPushDelivery();
            dbAck.setPushMessageId(msgId);
            dbAck.setTstamp(System.currentTimeMillis());
            dbAck.setUser(sndRec.getDestination().toBareJID());
            dbAck.setResource(sndRec.getDestination().getResource());
            dbAck.setStatus(statusCode);
            DbEntityManager.persistDbAck(dbAck);
        }
    }

    /**
     * Send recent push notifications for this JID in executor.
     * Called after on-connect or on-demand event. Transfer only non-ACKed push messages.
     * If there are no such messages, return.
     * @param from
     */
    public void sendRecentPushNotificationsInExecutor(final JID from){
        executor.submit("pushSend", new PushRunnable() {
            @Override
            public void run(PushService svc) {
                svc.sendRecentPushNotifications(from);
            }
        });
    }

    /**
     * Send recent push notifications for this JID.
     * Called after on-connect or on-demand event. Transfer only non-ACKed push messages.
     * If there are no such messages, return.
     * @param from
     */
    public void sendRecentPushNotifications(JID from){
        if (from == null){
            log.info("Empty address");
            return;
        }

        final Collection<DbPushMessage> msgs = DbEntityManager.getPushByUserAndAction(from.toBareJID(), null);
        final long tstamp = System.currentTimeMillis();
        final SimplePushMessage msgx = new SimplePushMessage(from.toBareJID(), tstamp);

        int added = 0;
        for (DbPushMessage msg : msgs) {
            final String action = msg.getAction();
            if (ClistSyncEventMessage.PUSH.equals(action)){
                msgx.addPart(new ClistSyncEventMessage(msg.getTstamp()));
                added += 1;

            } else if (NewCertEventMessage.PUSH.equals(action)){
                msgx.addPart(new NewCertEventMessage(msg.getTstamp(), Long.parseLong(msg.getAux1()), msg.getAux2()));
                added += 1;

            } else {
                log.error(String.format("Unknown DB message action %s", action));
            }
        }

        if (added == 0){
            log.info(String.format("Nothing from send for JID: %s", from));
        }

        // Send from all connected devices/resources.
        log.info(String.format("Sending push package from %s size %d", from, added));
        sendPush(from.asBareJID(), msgx);
    }

    /**
     * Send all relevant presence information to this user so he can refresh its presence status info.
     * @param from
     */
    public void sendPresenceInfoInExecutor(final JID from){
        executor.submit("presenceInfo", new PushRunnable() {
            @Override
            public void run(PushService svc) {
                svc.sendPresenceInfo(from);
            }
        });
    }

    /**
     * Send all relevant presence information to this user so he can refresh its presence status info.
     * @param from
     */
    public void sendPresenceInfo(JID from) {
        if (from == null){
            log.info("Empty address");
            return;
        }

        log.info(String.format("Going to send presence for roster for: %s", from));
        // TODO: implement....
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
