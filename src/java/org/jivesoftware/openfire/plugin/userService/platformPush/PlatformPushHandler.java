package org.jivesoftware.openfire.plugin.userService.platformPush;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.exceptions.NetworkIOException;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.Job;
import org.jivesoftware.openfire.plugin.userService.JobRunnable;
import org.jivesoftware.openfire.plugin.userService.clientState.ClientStateService;
import org.jivesoftware.openfire.plugin.userService.db.DbEntityManager;
import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.gcm.GcmSendRecord;
import org.jivesoftware.openfire.plugin.userService.platformPush.gcm.GcmSender;
import org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage.PushAck;
import org.jivesoftware.openfire.plugin.userService.platformPush.iq.PushMessageAckIq;
import org.jivesoftware.openfire.plugin.userService.platformPush.iq.PushMessageReqIq;
import org.jivesoftware.openfire.plugin.userService.platformPush.iq.PushTokenUpdateIq;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequest;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequestMessage;
import org.jivesoftware.openfire.plugin.userService.push.AckMergeRecord;
import org.jivesoftware.openfire.plugin.userService.push.PushSendRecord;
import org.jivesoftware.openfire.plugin.userService.utils.LRUCache;
import org.jivesoftware.openfire.plugin.userService.utils.MiscUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

import static com.notnoop.apns.EnhancedApnsNotification.*;

/**
 * Handles IQ related to the Apple push notifications registration.
 * Created by dusanklinec on 03.07.15.
 */
public class PlatformPushHandler extends IQHandler implements ServerFeaturesProvider, LRUCache.LRUCacheEvictionListener<String, PushMsgCacheCleanRec> {
    private static final Logger log = LoggerFactory.getLogger(PlatformPushHandler.class);
    private static final int MAX_REC_PER_USER_ACTION = 100;

    private UserServicePlugin plugin;
    private final LRUCache<JID, TokenConfig> tokenCache = new LRUCache<JID, TokenConfig>(128);

    /**
     * Best effor cache for last X push request messages stored in the database.
     * Should serve to avoid duplicate insertion. Optimize database access.
     */
    private final LRUCache<String, Integer> messageKeyCache = new LRUCache<String, Integer>(128);

    /**
     * Cache for runtime push message request cleaning.
     * If there is too many records inserted to the databse for given grouping key cleaning is performed
     * to keep database populated only with relevant records and to protect it from flooding.
     */
    private final LRUCache<String, PushMsgCacheCleanRec> pushMsgCleanCache = new LRUCache<String, PushMsgCacheCleanRec>(1024);

    /**
     * Apple push notification service
     */
    private ApnsService apnSvcProd;
    private ApnsService apnSvcDevel;

    /**
     * Apple push notification feedback service watcher - detection of invalid tokens.
     */
    private ApnFeedbackWatcher apnFeedbackWatcher;

    /**
     * Sender thread for GCM communication.
     */
    private GcmSender gcmSender;
    private final PriorityBlockingQueue<GcmSendRecord> gcmQueue = new PriorityBlockingQueue<GcmSendRecord>();

    public PlatformPushHandler(UserServicePlugin plugin) {
        super("ClientStateService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);
        tokenCache.clear();
        messageKeyCache.clear();
        pushMsgCleanCache.clear();
        pushMsgCleanCache.setEvictionListener(this);
        apnFeedbackWatcher = new ApnFeedbackWatcher(this);
        cleanPushMsgDb();

        try {
            apnSvcDevel = APNS.newService()
                    .withCert("/home/phonex/keys/apn_devel02.p12", "seeGiengoow5")
                    .withSandboxDestination()
                    .build();

            apnSvcProd = APNS.newService()
                    .withCert("/home/phonex/keys/apn_prod02.p12", "seeGiengoow5")
                    .withProductionDestination()
                    .build();

            apnFeedbackWatcher.start();
            log.info("APN service started");

        } catch(Exception e){
            apnSvcDevel = null;
            apnSvcProd = null;
            log.error("Cannot initialize Apple push notification library", e);
        }

        try {
            gcmSender = new GcmSender(this, plugin);
            gcmSender.start();

        } catch(Exception e){
            log.error("Exception in GCM initialization");
        }
    }

    public void deinit(){
        tokenCache.clear();
        messageKeyCache.clear();
        pushMsgCleanCache.clear();
        pushMsgCleanCache.setEvictionListener(null);
        apnFeedbackWatcher.deinit();
        gcmSender.deinit();

        try {
            if (apnSvcProd != null){
                apnSvcProd.stop();
            }
        } catch(Exception e){
            log.error("Could not stop production APN service", e);
        }

        try {
            if (apnSvcDevel != null){
                apnSvcDevel.stop();
            }
        } catch(Exception e){
            log.error("Could not stop devel APN service", e);
        }

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
     *  {"push":"newMsg", "tstamp":1435928809, "key":"122e"},
     *  {"push":"newMissedCall", "tstamp":1435928800, "key":"asssd"}
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
            log.warn("Error parsing JSON from the packet", e);
            return retPacket;
        }

        if (json == null){
            return retPacket;
        }

        PushParser parser = new PushParser();
        try {
            final PushAck pack = parser.processPushAck(json, packet);
            if (pack == null){
                log.warn("Error in parsing push request");
                return retPacket;
            }

            // TODO:[low] change ret packet so client has information which messages were received.
            // ...

            // Store to the database in background thread.
            plugin.submit("pushAckStore", new JobRunnable() {
                @Override
                public void run(UserServicePlugin plugin, Job job) {
                    // Persist all messages. If call cancellation is received, do not store it, delete message instead.
                    final int affected = DbEntityManager.persistPushAck(pack, true);
                    log.info(String.format("Push ACK received, num: %d, affected rows: %d", MiscUtils.collectionSize(pack.getMessages()), affected));
                }
            });

        } catch(Exception e){
            log.warn("Error in parsing request body", e);
            return retPacket;
        }

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
     *  {"push":"newCall", "key":"af45bed", "cancel":1}
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

        PushParser parser = new PushParser();
        try {
            final PushRequest request = parser.processPushRequest(json, packet);
            if (request == null){
                log.warn("Error in parsing push request");
                return retPacket;
            }

            // Process new push request message, add to queue and invoke sending.
            onNewPushReq(request, true);

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

            // Store to the LRU cache for fast lookup.
            tokenCache.put(from, tokenConfig);

            // Store to the database. Update if the JID is same.
            plugin.submit("tokenStore", new JobRunnable() {
                @Override
                public void run(UserServicePlugin plugin, Job job) {
                    DbEntityManager.persistAppleTokenConfig(tokenConfig);

                    // Trigger broadcast of the stored messages.
                    triggerUserPushRecheck(null, tokenConfig);

                    // TODO: we have a new valid token -> reflect this in the last active record.
                    // ...
                }
            });

        } catch (JSONException e) {
            log.warn("Error parsing JSON from the packet");
            return retPacket;
        }

        return retPacket;
    }

    /**
     * Handling new push requests.
     * @param request
     * @return
     */
    public boolean onNewPushReq(final PushRequest request, final boolean cameFromUser){
        if (request == null){
            log.warn("Error in parsing push request");
            return false;
        }

        final String bareJidUser = request.getFromUser().toBareJID();

        // Store to the database in background thread.
        plugin.submit("pushReqStore", new JobRunnable() {
            @Override
            public void run(UserServicePlugin plugin, Job job) {
                // Persist all messages. If call cancellation is received, do not store it, delete message instead.
                int affected = 0;
                final long curTime = System.currentTimeMillis();
                final Set<String> usersToNotify = new HashSet<String>();
                final List<PushRequestMessage> requests = request.getMessages();
                final JID fromUser = request.getFromUser();
                final UserServicePlugin usrPlugin = getPlugin();
                final ClientStateService cStateSvc = usrPlugin.getCstateSvc();

                for (PushRequestMessage curReq : requests) {
                    final JID toUser = curReq.getToUser();

                    // Do we know given action of the message? Protect from flooding with rubbish action names.
                    // We do not support universal, forward compatible action names as APN is not that straighforward mechanism
                    // as simple XMPP push notifications.
                    final String action = curReq.getAction();
                    final PushRequestMessage msgByAction = PushParser.getMessageByAction(action, false);
                    if (msgByAction == null){
                        log.warn(String.format("Ignoring push message request with unknown action name %s", action));
                        continue;
                    }

                    // Check roster permissions.
                    if (fromUser != null && !usrPlugin.canProbePresence(fromUser, toUser.getNode())){
                        log.warn(String.format("User %s cannot send push notification to %s, blocked by roster permission model",
                                fromUser, toUser.toBareJID()));
                        continue;
                    }

                    // Check if given message can be requested by end user.
                    if (cameFromUser && !curReq.isCanUserRequest()){
                        log.warn(String.format("User %s attempted to request push message delivery blocked by policy %s", fromUser, curReq.getAction()));
                        continue;
                    }

//                    // If last activity was recently active, do not trigger push, as the client is working with the app.
//                    // TODO: fix when multiuser support is added.
//                    final ActivityRecord toUserLastActivity = cStateSvc.getLastActivity(toUser.asBareJID());
//                    if (toUserLastActivity != null && !toUserLastActivity.isSentinel()){
//                        final long lastActiveMilli = toUserLastActivity.getLastActiveMilli();
//                        if (toUserLastActivity.getLastState() == ActivityRecord.STATE_ACTIVE && (curTime - lastActiveMilli) > 1000*60*5){
//                            log.info(String.format("Push from %s to %s, action %s is discarded, destination is active",
//                                    fromUser, toUser, action));
//                            continue;
//                        }
//                    }

                    // Check cache if this op was not performed recently.
                    final boolean doCache = curReq.getKey() != null && !curReq.getKey().isEmpty();
                    final String cacheKey = doCache ? "c:" + curReq.isCancel() + ";key:" + curReq.getKey() + ";usr:" + bareJidUser : null;
                    if (doCache && messageKeyCache.containsKey(cacheKey)) {
                        log.debug(String.format("Duplicate operation detected, key: %s", cacheKey));
                    }

                    // TODO: In order to support multi-device setup, adapt same delivery mechanism
                    // TODO:   like we have in normal push messages, addressing by resource ID,
                    // TODO:   routing messages via tokens tied to resource IDs.
                    // TODO: On ACK receipt, create a delivery record and for next push request do not deliver the same message
                    // TODO:   to the given resource which ACKed the message. Still can be delivered to another devices/tokens/resources
                    // TODO:   from the same user which do not ACK message. Rethink the concept. If Acked on some device, maybe
                    // TODO:   we want to consider it ACKed on all devices as the same user does it.

                    // Persisting to the database + protection against flooding / errors. Keeps last 100 records
                    // for user-action pairs.
                    boolean success = DbEntityManager.persistNewPushRequest(request, curReq);
                    if (success) {
                        affected += 1;
                        usersToNotify.add(toUser.toBareJID());

                        // Reflect change to the database in the push message database cleaning cache.
                        handleNewPushRequestAdded(toUser, request, curReq);
                    }

                    if (success && doCache) {
                        messageKeyCache.put(cacheKey, Integer.MIN_VALUE);
                    }
                }

                // Trigger sending all unacknowledged push messages to the client via APN. this job loads all push messages to send.
                if (!usersToNotify.isEmpty()) {
                    triggerUserPushRecheck(usersToNotify, null);
                }
            }
        });

        return true;
    }

    /**
     * Triggers sending all available push messages for given users.
     * @param users
     */
    public void triggerUserPushRecheck(final Collection<String> users){
        triggerUserPushRecheck(users, null);
    }

    /**
     * Triggers procedure to recheck database for waiting toUser. Expired messages are ignored, not loaded.
     * This trigger leads to sending push notification to a given user. Should be called with care when there is a reason.
     * @param users    Collection of the users for which the re-check should be performed. Ignored when forToken is not null.
     * @param forToken If not null, push messages are dumped only to this token. Otherwise for all tokens registered for given user.
     *                 Useful when a new token is registered to dump waiting push messages to a new token, not to everybody.
     *                 In this manner we avoid duplicate reception.
     */
    public void triggerUserPushRecheck(final Collection<String> users, final TokenConfig forToken){
        plugin.submit("reqRecheck", new JobRunnable() {
            @Override
            public void run(UserServicePlugin plugin, Job job) {
                triggerUserPushRecheckInt(users, forToken);
            }
        });
    }

    /**
     * {@see PlatformPushHandler.triggerUserPushRecheck}
     * This code should be already executed in the background threads.
     *
     * @param users
     * @param forToken
     */
    public void triggerUserPushRecheckInt(Collection<String> users, TokenConfig forToken){
        // Load user : token database.
        final Map<String, List<TokenConfig>> tokenDatabase = new HashMap<String, List<TokenConfig>>();
        if (forToken != null){
            // Only single token was given. Do it for this single token as it was added recently.
            final List<TokenConfig> singleList = new ArrayList<TokenConfig>();
            singleList.add(forToken);
            tokenDatabase.put(forToken.getUser().toBareJID(), singleList);

        } else {
            // Load all tokens for given set of users.
            final List<TokenConfig> tokenList = DbEntityManager.loadTokens(users);
            for(TokenConfig curToken : tokenList){
                final JID user = curToken.getUser();
                final String bareUser = user.toBareJID();

                if (!tokenDatabase.containsKey(bareUser)){
                    tokenDatabase.put(bareUser, new ArrayList<TokenConfig>());
                }

                final List<TokenConfig> lst = tokenDatabase.get(bareUser);
                lst.add(curToken);
            }

        }

        triggerPushReqRecheckOnUsers(tokenDatabase);
    }

    /**
     * Should be called on the internal thread.
     * The recheck will be performed only for users specified as map keys.
     * APN will be sent only to specified tokens.
     *
     * @param tokenDatabase bare_username -> List<TokenConfig> mapping.
     */
    private void triggerPushReqRecheckOnUsers(Map<String, List<TokenConfig>> tokenDatabase){
        if (tokenDatabase == null || tokenDatabase.isEmpty()){
            return;
        }

        // If APN service is down, nothing to do.
        if (apnSvcDevel == null || apnSvcProd == null){
            log.warn("Could not use Apple push, service is null");
            return;
        }

        final Set<String> userSet = tokenDatabase.keySet();
        final List<DbPlatformPush> reqs = DbEntityManager.loadPushRequestMessages(userSet);
        final Map<String, List<DbPlatformPush>> usrRequests = new HashMap<String, List<DbPlatformPush>>();

        // Group messages by toUser field to a list.
        for(DbPlatformPush ppush : reqs){
            final String user = ppush.getForUser();
            List<DbPlatformPush> lstToUse = null;

            if (!usrRequests.containsKey(user)){
                lstToUse = new ArrayList<DbPlatformPush>();
                usrRequests.put(user, lstToUse);

            } else {
                lstToUse = usrRequests.get(user);

            }

            lstToUse.add(ppush);
        }

        // Group messages by actionName. Compute badge numbers, highest priority.
        for(String user : usrRequests.keySet()){
            final List<DbPlatformPush> list = usrRequests.get(user);
            final List<TokenConfig> tokens = tokenDatabase.get(user);

            ApnPushBuilder builder = new ApnPushBuilder();
            builder.setStrings(plugin.getStrings())
                    .setUser(user)
                    .setTokens(tokens)
                    .setPushMessagesList(list);

            try {
                // TODO: if this message contains push request that has active expiration time, e.g. incoming call,
                // TODO: it should affect the expiration of the wrapping message. After this expiration time is over
                // TODO: this service should post a new push notification send job, this time without expired notification.
                for(TokenConfig token : tokens){
                    // For each token build separately - different language settings?
                    builder.buildForToken(token);
                    final String payload = builder.getPayload();

                    // TODO: when expiration is correctly implemented for expiring notifications, fix this.
                    // TODO: Expiration is useful for active call. After call is expired, new push notification without call

                    // Send to a particular token, depending on the platform.
                    // TODO: add builder to the send job and remove non-ACK messages after sending succeeds.
                    sendTokenPayload(token, payload, builder);
                }

                // Delete non-ack messages from the database.
                // iterate list, remove those not having wait ack set.
                final int affectedNonAck = DbEntityManager.deleteNoAckWaitPushRequests(list);
                log.info(String.format("Sent finished, non-ack deleted: %d", affectedNonAck));

            } catch (JSONException e) {
                log.error("Exception in generating APN", e);
            }
        }
    }

    /**
     * Sends given payload to a
     * @param token
     * @param builder
     */
    protected void sendTokenPayload(TokenConfig token, String payload, ApnPushBuilder builder) throws JSONException {
        // Apple token:
        if (token.isIos()) {
            final int now = (int)(new Date().getTime()/1000);
            final EnhancedApnsNotification notification = new EnhancedApnsNotification(
                    INCREMENT_ID() /* Next ID */,
                    now + 60 * 60 * 24 * 30 /* Expire in 30 days */,
                    token.getToken() /* Device Token */,
                    payload);

            try {
                if (token.getDebug()) {
                    log.info(String.format("Broadcasting devel push message, to: %s, payload: %s", token.getUser(), payload));
                    apnSvcDevel.push(notification);

                } else {
                    log.info(String.format("Broadcasting production push message, to: %s, payload: %s", token.getUser(), payload));
                    apnSvcProd.push(notification);
                }

                // TODO: delete non-ACK message as send was successful.
            } catch(NetworkIOException e){
                log.error("Exception in sending APN", e);
                throw e;
            }

        } else if (token.isAndroid()){
            final Message.Builder gcmMsgBuilder = new Message.Builder();
            gcmMsgBuilder.contentAvailable(true);
            gcmMsgBuilder.timeToLive(2419000); // Maximum allowed TTL
            gcmMsgBuilder.restrictedPackageName("net.phonex");
            gcmMsgBuilder.addData("phxroot", payload);
            final Message gcmMsg = gcmMsgBuilder.build();

            GcmSendRecord sndRec = new GcmSendRecord();
            sndRec.setTo(token.getToken());
            sndRec.setPushMsg(gcmMsg);
            sndRec.setBuilder(builder);

            log.info(String.format("Broadcasting GCM push message, to: %s, payload: %s", token.getUser(), payload));
            addSendRecord(sndRec, true);
        }
    }

    /**
     * Callback from apple push notification watcher
     * @param tokens
     * @param devel
     */
    public void disabledTokensDetected(Set<String> tokens, boolean devel) {
        if (tokens == null || tokens.isEmpty()){
            return;
        }

        // TODO: update token cache.
        // TODO: reflect this to the last-active records.
        log.info("Detected %d of disabled tokens, devel=%s", tokens.size(), devel);
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

    public ApnsService getApnSvcProd() {
        return apnSvcProd;
    }

    public ApnsService getApnSvcDevel() {
        return apnSvcDevel;
    }

    /**
     * New push request was added to the database.
     * This call should keep database clean, keep only relevant data, protect db from flooding.
     * @param user
     * @param curReq
     * @param reqMsg
     */
    protected void handleNewPushRequestAdded(JID user, PushRequest curReq, PushRequestMessage reqMsg){
        final String action = reqMsg.getAction();
        final String cacheKey = getCacheKeyPushMsgClean(user, action);

        // If there is no cache record, create a new one and exit.
        final PushMsgCacheCleanRec cacheRec = pushMsgCleanCache.get(cacheKey);
        if (cacheRec == null){
            PushMsgCacheCleanRec rec = new PushMsgCacheCleanRec();
            rec.setTimestamp(curReq.getTstamp());
            rec.setRecordsAhead(0);
            rec.setUser(user.toBareJID());
            rec.setAction(action);

            pushMsgCleanCache.put(cacheKey, rec);
            return;
        }

        PushMsgCacheCleanRec copy;
        synchronized (cacheRec.getMutex()) {
            final int newRecAhead = cacheRec.getRecordsAhead() + 1;
            if (newRecAhead < MAX_REC_PER_USER_ACTION) {
                cacheRec.setRecordsAhead(newRecAhead);
                return;
            }

            // Copy to a new cache rec.
            copy = PushMsgCacheCleanRec.copyFrom(cacheRec);

            // Clean cache ahead records.
            cacheRec.setTimestamp(curReq.getTstamp());
            cacheRec.setRecordsAhead(0);
        }

        // Cleaning.
        clearPushRequestDb(copy);
    }

    /**
     * Performs DB cleanup w.r.t. given record.
     */
    protected void clearPushRequestDb(PushMsgCacheCleanRec rec){
        // If there are no more records ahead of this one, do nothing.
        if (rec == null || rec.getRecordsAhead() == 0){
            return;
        }

        // DB cleanup for given record.
        JID user = new JID(rec.getUser());
        DbEntityManager.deletePushRequestsOlderThan(user, rec.getAction(), rec.getTimestamp());
    }

    /**
     * Returns cache key for
     * @param user
     * @param action
     * @return
     */
    protected String getCacheKeyPushMsgClean(JID user, String action){
        return user.toBareJID() + "_;_" + action;
    }

    /**
     * Listen to eviction events on the cache.
     * This says given record is going to be removed from the cache as the last accessed item.
     * @param eldest
     */
    @Override
    public void onEntryEvicted(LRUCache<String, PushMsgCacheCleanRec> cache, Map.Entry<String, PushMsgCacheCleanRec> eldest) {
        if (eldest == null){
            return;
        }

        final PushMsgCacheCleanRec rec = eldest.getValue();
        if (rec == null){
            return;
        }

        // On eviction cleaning SQL is needed to be called, because we may loose a lot of
        // records, e.g., in case recordsAhead == 1, all older records get deleted, only 1 record remains, not 100.
        plugin.submit("evictClean", new JobRunnable() {
            @Override
            public void run(UserServicePlugin plugin, Job job) {
                // Persist all messages. If call cancellation is received, do not store it, delete message instead.
                final JID user = new JID(rec.getUser());
                final int affected = DbEntityManager.cleanDbPushRequestDb(MAX_REC_PER_USER_ACTION, user, rec.getAction());
                log.info(String.format("Eviction cleaning, affected rows: %d", affected));
            }
        });
    }

    /**
     * Clean push message request database. Expensive call, performed in the initialization phase.
     */
    public void cleanPushMsgDb(){
        Thread cleanThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("<push_db_clean>");
                    int affected = DbEntityManager.cleanDbPushRequestDb(MAX_REC_PER_USER_ACTION);
                    log.info(String.format("Push DB cleaned, entries removed: %d. </push_db_clean>", affected));
                } catch(Exception e){
                    log.error("Exception in clean thread", e);
                }
            }
        });
        cleanThread.start();
    }

    /**
     * Entry point for new incoming push message sent to XMPP queue.
     * Accepts push request messages, from the queue.
     *
     * {"action"    :"pushReq",
     *  "user"      :"test1@phone-x.net",
     *  "pushreq"   :[
     *    {"push":"newEvent",       "target": "test-internal3@phone-x.net", "tstamp": 1446487435000},
     *    {"push":"newAttention",   "target": "test-internal3@phone-x.net"},
     *    {"push":"newMessage",     "target": "test-internal3@phone-x.net"},
     *    {"push":"newOfflineMsg",  "target": "test-internal3@phone-x.net", "tstamp": 1446487435000},
     *    {"push":"newMissedCall",  "target": "test-internal3@phone-x.net"},
     *    {"push":"newCall",        "target": "test-internal3@phone-x.net", "key":"af45bed", "expire":180000,}
     *  ]
     *  }
     *
     * @param obj
     */
    public void handlePushRequestFromQueue(JSONObject obj) {
        try {
            final String fromUser = obj.has("user") ? obj.getString("user") : null;
            PushParser parser = new PushParser();

            final PushRequest request = parser.processPushRequest(obj, fromUser == null ? null : new JID(fromUser));
            if (request == null){
                log.warn("Error in parsing push request");
                return;
            }

            // Process new push request message, add to queue and invoke sending.
            onNewPushReq(request, false);

        } catch(Exception e){
            log.error("Exception in push request from queue handling", e);
        }
    }

    /**
     * Entry point for new incoming RAW push message sent to XMPP queue.
     * Accepts push request messages, from the queue.
     *
     * {"action"    :"pushRaw",
     *  "user"      :"test1@phone-x.net",
     *  "push" :{
     *      "aps" : {
     *          "alert" : {
     *              "title" : "Game Request",
     *              "body" : "Bob wants to play poker",
     *              "action-loc-key" : "PLAY"
     *          },
     *          "badge" : 5,
     *      },
     *      "acme1" : "bar",
     *      "acme2" : [ "bang",  "whiz" ]
     *   }
     *  }
     *
     * @param obj
     */
    public void handlePushRawRequestFromQueue(JSONObject obj) {
        try {
            final String toUser = obj.getString("user");
            final JSONObject pushMsg = obj.getJSONObject("push");

            // Load all tokens for given set of users.
            final List<TokenConfig> tokenList = DbEntityManager.loadTokens(Collections.singletonList(toUser));
            for(TokenConfig curToken : tokenList) {
                try {
                    sendTokenPayload(curToken, pushMsg.toString(), null);
                } catch (Exception ex) {
                    log.error("Exception in sending a push to particular token", ex);
                }
            }

        } catch(Exception e){
            log.error("Exception in push request from queue handling", e);
        }
    }

    /**
     * Triggers sending a DB push messages to the given user.
     * {"action"    :"pushTrigger",
     *  "user"      :"test1@phone-x.net"
     *  }
     *
     * @param obj
     */
    public void handlePushTriggerRequestFromQueue(JSONObject obj) {
        try {
            final String toUser = obj.getString("user");
            log.info(String.format("Starting trigger push job for user %s", toUser));
            triggerUserPushRecheck(Collections.singletonList(toUser));

        } catch(Exception e){
            log.error("Exception in push request from queue handling", e);
        }
    }

    /**
     * Main entry to submit new push notifications to the queue.
     * Try to merge with existing waiting message to save space / messages / bandwidth.
     * @param sndRec
     * @param tryMerge
     */
    public void addSendRecord(GcmSendRecord sndRec, boolean tryMerge) {
        gcmQueue.add(sndRec);
    }

    /**
     * Called by sender when GCM sending failed.
     * @param sndRec
     */
    public void onGcmSendFailed(GcmSendRecord sndRec){
        log.info("GCM sending failed for " + sndRec);

        // TODO: mark all messages related to this sending record as not sent - in a thread safe way.
        // TODO    Beware of multiple push messages carrying subsets of push requests. If one fails another can succeed
        // TODO    thus take care if some message is not already ACKed. => Store positive ACKs. None ack = no delivery.
    }

    public void onGcmSendSuccess(GcmSendRecord sndRec, Result gcmResult) {
        log.info("GCM sending success");

        // TODO: delete non-ack db messages.
    }

    public PriorityBlockingQueue<GcmSendRecord> getGcmQueue() {
        return gcmQueue;
    }

}
