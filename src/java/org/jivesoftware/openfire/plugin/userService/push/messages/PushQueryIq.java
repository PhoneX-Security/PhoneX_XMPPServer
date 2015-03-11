package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.xmpp.packet.IQ;

/**
 * Created by dusanklinec on 11.03.15.
 */
public class PushQueryIq extends IQ {
    public static final String ELEMENT_NAME = "pushQuery";
    public static final String NAMESPACE = "urn:xmpp:phx";
    public static final IQHandlerInfo info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);

    /**
     * Default constructor.
     */
    public PushQueryIq() {
        super(IQ.Type.get);
    }


}
