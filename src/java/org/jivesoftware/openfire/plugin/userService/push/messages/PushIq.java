package org.jivesoftware.openfire.plugin.userService.push.messages;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.json.JSONException;
import org.xmpp.packet.IQ;

/**
 * IQ for push message. Contains JSON push update.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class PushIq extends IQ {
    public static final String ELEMENT_NAME = "push";
    public static final String NAMESPACE = "urn:xmpp:phx";
    public static final IQHandlerInfo info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);

    /**
     * Default constructor.
     */
    public PushIq() {
        super(IQ.Type.get);
    }

    /**
     * Update content with given push message.
     * @param msg
     */
    public void setContent(SimplePushMessage msg) throws JSONException{
        Element pushElem = this.setChildElement(ELEMENT_NAME, NAMESPACE);
        pushElem.addAttribute("version", "1");

        Element jsonElement = pushElem.addElement("json");
        jsonElement.addCDATA(msg.getJson().toString());
    }
}
