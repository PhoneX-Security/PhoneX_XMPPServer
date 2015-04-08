package org.jivesoftware.openfire.plugin.userService.clientState;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.xmpp.packet.IQ;

/**
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
        super(IQ.Type.get);
    }
}
