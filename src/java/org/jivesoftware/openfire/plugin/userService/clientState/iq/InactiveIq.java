package org.jivesoftware.openfire.plugin.userService.clientState.iq;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.xmpp.packet.IQ;

/**
 * Sent by client when user stops working with the application, i.e., application went to background.
 * Notifies the server to stop sending presence updates in order to save battery, network and do not trigger iOS killing policies.
 * Created by dusanklinec on 06.04.15.
 */
public class InactiveIq  extends IQ {
    public static final String ELEMENT_NAME = "inactive";
    public static final String NAMESPACE = "urn:xmpp:phxClient";
    public static final IQHandlerInfo info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);

    /**
     * Default constructor.
     */
    public InactiveIq() {
        super(IQ.Type.set);
    }
}
