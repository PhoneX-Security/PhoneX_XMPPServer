package org.jivesoftware.openfire.plugin.userService.push;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.userService.push.messages.PushQueryIq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

import java.util.Collections;
import java.util.Iterator;

/**
 * Handle pushQuery from client to obtain all recent push notifications.
 * Created by dusanklinec on 11.03.15.
 */
public class PushQueryHandler extends IQHandler implements ServerFeaturesProvider {
    private static final Logger log = LoggerFactory.getLogger(PushQueryHandler.class);
    private PushService svc;

    public PushQueryHandler() {
        super("PushQueryHandler");
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

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        final IQ.Type iqType = packet.getType();
        log.info(String.format("Handle IQ packet_type: %s", iqType));

        // Handle only specific get requests.
        if (!IQ.Type.get.equals(iqType)) {
            return null;
        }

        if (svc == null){
            return null;
        }

        svc.sendRecentPushNotifications(packet.getFrom());
        IQ result = IQ.createResultIQ(packet);
        return result;
    }

    @Override
    public IQHandlerInfo getInfo() {
        return PushQueryIq.info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(PushQueryIq.NAMESPACE).iterator();
    }

    public PushService getSvc() {
        return svc;
    }

    public void setSvc(PushService svc) {
        this.svc = svc;
    }
}
