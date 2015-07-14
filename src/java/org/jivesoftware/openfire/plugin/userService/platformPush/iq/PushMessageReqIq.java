package org.jivesoftware.openfire.plugin.userService.platformPush.iq;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmpp.packet.IQ;

/**
 * Acknowledgements for platform push messages so they are not sent multiple times by the platform
 * push service to the device.
 *
 * Created by dusanklinec on 03.07.15.
 */
public class PushMessageReqIq extends IQ {
    public static final String ELEMENT_NAME = "req";
    public static final String NAMESPACE = "urn:xmpp:ppush";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_JSON = "json";
    public static final IQHandlerInfo info = new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);

    /**
     * Default constructor.
     */
    public PushMessageReqIq() {
        super(Type.set);
    }

    /**
     * Update content with given push message.
     *
     * @param jsonObj
     */
    public void setContent(JSONObject jsonObj) throws JSONException {
        Element pushElem = this.setChildElement(ELEMENT_NAME, NAMESPACE);
        pushElem.addAttribute(FIELD_VERSION, "1");

        Element jsonElement = pushElem.addElement(FIELD_JSON);
        jsonElement.addCDATA(jsonObj.toString());
    }
}
