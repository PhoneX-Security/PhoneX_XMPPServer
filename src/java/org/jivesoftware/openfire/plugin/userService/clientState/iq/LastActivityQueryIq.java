package org.jivesoftware.openfire.plugin.userService.clientState.iq;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.xmpp.packet.IQ;

/**
 * Used to query for the last activity of the contact.
 * Created by dusanklinec on 03.07.15.
 */
public class LastActivityQueryIq  extends IQ {
    public static final String ELEMENT_NAME = "lastActivityQuery";
    public static final String NAMESPACE = "urn:xmpp:phxClient";
    public static final IQHandlerInfo info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);

    /**
     * Default constructor.
     */
    public LastActivityQueryIq() {
        super(Type.get);
    }

}