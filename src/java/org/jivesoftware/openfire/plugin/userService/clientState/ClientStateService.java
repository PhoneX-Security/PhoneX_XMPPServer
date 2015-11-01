package org.jivesoftware.openfire.plugin.userService.clientState;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.Job;
import org.jivesoftware.openfire.plugin.userService.JobRunnable;
import org.jivesoftware.openfire.plugin.userService.clientState.iq.ActiveIq;
import org.jivesoftware.openfire.plugin.userService.clientState.iq.InactiveIq;
import org.jivesoftware.openfire.plugin.userService.clientState.iq.LastActivityQueryIq;
import org.jivesoftware.openfire.plugin.userService.db.DbEntityManager;
import org.jivesoftware.openfire.plugin.userService.platformPush.TokenConfig;
import org.jivesoftware.openfire.plugin.userService.utils.LRUCache;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.Collections;
import java.util.Iterator;

/**
 * Service implements XEP-0352 in our way.
 * Device may indicate active/inactive state. When in inactive state, all presence updates are blocked until he either
 * re-authenticates or switches to active state again.
 *
 * http://xmpp.org/extensions/xep-0352.html
 *
 * Created by dusanklinec on 06.04.15.
 */
public class ClientStateService extends IQHandler implements ServerFeaturesProvider, PacketInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ClientStateService.class);
    public static final String INACTIVE_KEY = "phx_inactive";

    private UserServicePlugin plugin;

    /**
     * Caching last activity for the user.
     */
    private final LRUCache<JID, ActivityRecord> activityCache = new LRUCache<JID, ActivityRecord>(128);
    private final LRUCache<String, ActivityRecord> activityCacheUsr = new LRUCache<String, ActivityRecord>(128);

    public ClientStateService(UserServicePlugin plugin) {
        super("ClientStateService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);
        InterceptorManager intManager = InterceptorManager.getInstance();
        intManager.addInterceptor(this);
        activityCache.clear();
        activityCacheUsr.clear();
    }

    public void deinit(){
        activityCache.clear();
        activityCacheUsr.clear();

        // Remove this as IQ listener.
        try {
            IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            iqRouter.removeHandler(this);
        } catch (Exception ex) {
            log.error("Could not unregister from IQ router", ex);
        }

        try {
            InterceptorManager intManager = InterceptorManager.getInstance();
            intManager.removeInterceptor(this);
        } catch(Exception ex){
            log.error("Could not remove packet interceptor", ex);
        }
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        final IQ.Type iqType = packet.getType();
        log.info(String.format("Handle IQ[clientState] packetType: %s, from: %s, to: %s, tstamp: %d",
                iqType, packet.getFrom(), packet.getTo(), System.currentTimeMillis()));

        log.info(packet.toString());

        if (plugin == null){
            log.info("No svc, cannot process");
            return null;
        }

        final Element elem = packet.getChildElement();
        if (elem == null){
            return null;
        }

        final String tagName = elem.getName();
        if (ActiveIq.ELEMENT_NAME.equals(tagName)){
            // Active state set.
            setActivityFlag(packet.getFrom(), true);

        } else if (InactiveIq.ELEMENT_NAME.equals(tagName)) {
            // Inactive state set.
            setActivityFlag(packet.getFrom(), false);

        } else if (LastActivityQueryIq.ELEMENT_NAME.equals(tagName)){
            // Handle query for last activity.
            return handleLastActivityQuery(packet, packet.getFrom());

        } else {
            return IQ.createResultIQ(packet);
        }

        return IQ.createResultIQ(packet);
    }

    /**
     * Stores activity flag for the current user to its local session data.
     * @param from
     * @param active
     */
    private void setActivityFlag(JID from, boolean active){
        try {
            // Store last activity to the database so it can be retrieved later.
            storeActivityRecord(from, !active);

            // Activity info stored to the session, for intercepting presence updates.
            Session sess = sessionManager.getSession(from);
            if (sess instanceof LocalSession){
                final LocalSession localSession = (LocalSession) sess;
                localSession.setSessionData(INACTIVE_KEY, !active);
                log.info(String.format("User %s set activity flag to %s", from, active));

                if (active){
                    // Push all current presence updates to the user.
                    plugin.getPushSvc().sendPresenceInfoInTaskExecutor(from);

                    // Push all remote notifications so they are accepted by the application.
                    plugin.getpPushSvc().triggerUserPushRecheck(Collections.singletonList(from.toBareJID()));
                }

            } else {
                log.info("Session not instance of local session for user: " + from);
            }

        } catch (Exception ex) {
            log.error("Exception in setting activity", ex);
        }
    }

    /**
     * Stores activity record for given user to the cache + database.
     * TODO: add grouping so we do not update database with each activity update. e.g. if user changes activity 100x a minute, update only once a minute.
     * @param user
     * @param wentInactive
     */
    protected void storeActivityRecord(JID user, boolean wentInactive){
        final ActivityRecord ar = new ActivityRecord(
                user,
                System.currentTimeMillis(),
                wentInactive ? ActivityRecord.STATE_INACTIVE : ActivityRecord.STATE_ACTIVE
        );

        // Store activity record to the cache for fast retrieval.
        activityCache.put(user, ar);
        // Newest in username based cache.
        final String uname = user.toBareJID();
        final ActivityRecord usrAr = activityCacheUsr.get(uname);
        if (usrAr == null || usrAr.isSentinel() || usrAr.getLastActiveMilli() < ar.getLastActiveMilli()){
            activityCacheUsr.put(uname, ar);
        }

        // Store activity record to the database to survive service restart & cache evictions. Do it in background.
        plugin.submit("lastActivityStore", new JobRunnable() {
            @Override
            public void run(UserServicePlugin plugin, Job job) {
                DbEntityManager.persistLastActivity(ar);
            }
        });
    }

    /**
     * Tries to retrieve last activity for the user.
     * Using cache and database layer.
     *
     * @param user
     * @return
     */
    public ActivityRecord getLastActivity(JID user){
        // Try cache lookup. Since we have sentinel records we do not have to query database each time for non-existent records.
        final String resource = user.getResource();
        final boolean usrGlobal = resource == null || resource.isEmpty();
        if (usrGlobal){
            // If resource is empty, search in user global cache.
            final ActivityRecord ar = activityCacheUsr.get(user.toBareJID());
            if (ar != null){
                return ar;
            }
        } else {
            // User + resource cache.
            final ActivityRecord ar = activityCache.get(user);
            if (ar != null) {
                return ar;
            }
        }

        // Cache miss, try to load from database. Update cache from database.
        final ActivityRecord ar2 = DbEntityManager.getLastActivity(user);
        if (ar2 == null){
            final ActivityRecord sentinel = new ActivityRecord(user, true);

            if (usrGlobal){
                activityCacheUsr.put(user.toBareJID(), sentinel);
            } else {
                activityCache.put(user, sentinel);
                activityCacheUsr.put(user.toBareJID(), sentinel);
            }

            return sentinel;
        }

        if (usrGlobal){
            activityCacheUsr.put(user.toBareJID(), ar2);
        } else {
            activityCache.put(user, ar2);
            activityCacheUsr.put(user.toBareJID(), ar2);
        }

        return ar2;
    }

    /**
     * Handles IQ resuests for last activity of given user.
     * @param packet
     * @param user
     * @return
     */
    protected IQ handleLastActivityQuery(IQ packet, JID user){
        // TODO: check roster permissions, allow it only if presence update is allowed too.


        // TODO: implement.
//        final ActivityRecord ar = getLastActivity(user);
        return IQ.createResultIQ(packet);
    }

    /**
     * Main intercepting point for packets when JID is in inactive mode.
     * Basically filter all presence updates when in inactive mode.
     * @param packet
     * @param session
     * @param incoming
     * @param processed
     * @throws PacketRejectedException
     */
    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        // Interested only in outgoing packets to our connected clients. Clients are throttling incoming packets.
        // Take only those not processed.
        if (incoming || processed){
            return;
        }

        // Currently only local session is supported since the inactivity flag is stored to the local session data.
        if (!(session instanceof LocalSession)){
            return;
        }

        // Only presence packet are subject to filtering. If is a different, do nothing here, let packet pass.
        if (!(packet instanceof Presence)){
            return;
        }

        try {
            final LocalSession sess = (LocalSession) session;
            Boolean inactive = (Boolean) sess.getSessionData(INACTIVE_KEY);

            // Leave packet if there is no record about inactivity (by default set to active) or is active at the moment.
            if (inactive == null || !inactive) {
                return;
            }
        } catch(Exception ex){
            log.error("Exception in deciding whether to drop packet or not", ex);
        }

        // Inactive flag was found and is true, block presence packet.
        throw new PacketRejectedException();
    }

    @Override
    public IQHandlerInfo getInfo() {
        return ActiveIq.info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(ActiveIq.NAMESPACE).iterator();
    }

    public UserServicePlugin getPlugin() {
        return plugin;
    }

    public void setPlugin(UserServicePlugin plugin) {
        this.plugin = plugin;
    }
}
