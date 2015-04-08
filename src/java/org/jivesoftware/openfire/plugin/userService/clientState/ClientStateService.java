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
    private static final String INACTIVE_KEY = "phx_inactive";

    private UserServicePlugin plugin;

    public ClientStateService(UserServicePlugin plugin) {
        super("ClientStateService");
        this.plugin = plugin;
    }

    public void init() {
        IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
        iqRouter.addHandler(this);
        InterceptorManager intManager = InterceptorManager.getInstance();
        intManager.addInterceptor(this);
    }

    public void deinit(){
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
        log.info(String.format("Handle IQ[clientState] packetType: %s, from: %s, to: %s", iqType, packet.getFrom(), packet.getTo()));
        log.info(packet.toString());

        // Handle only specific get requests.
        if (!IQ.Type.set.equals(iqType)) {
            log.info("Undesired type, not processing");
            return null;
        }

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

        } else if (InactiveIq.ELEMENT_NAME.equals(tagName)){
            // Inactive state set.
            setActivityFlag(packet.getFrom(), false);

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
            Session sess = sessionManager.getSession(from);
            if (sess instanceof LocalSession){
                final LocalSession localSession = (LocalSession) sess;
                localSession.setSessionData(INACTIVE_KEY, !active);
                log.info(String.format("User %s set activity flag to %s", from, active));

                // Push all current presence updates to the user.
                if (active){
                    plugin.getPushSvc().sendPresenceInfoInExecutor(from);
                }

            } else {
                log.info("Session not instance of local session for user: " + from);
            }

        } catch (Exception ex) {
            log.error("Exception in setting activity", ex);
        }
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
