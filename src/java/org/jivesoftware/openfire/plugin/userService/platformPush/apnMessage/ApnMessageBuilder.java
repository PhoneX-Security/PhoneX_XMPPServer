package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dusanklinec on 17.07.15.
 */
public class ApnMessageBuilder {
    private static final Logger log = LoggerFactory.getLogger(ApnMessageBuilder.class);

    /**
     * Constructs Apple push notification message from database push.
     * @param ppush
     * @return
     */
    public static ApnMessage buildMessageFromDb(DbPlatformPush ppush, boolean allowGeneric){
        final String action = ppush.getAction();
        if (action == null || action.isEmpty()){
            return null;
        }

        ApnMessageBase ab;
        if (NewActiveCallPush.ACTION.equals(action)){
            ab = new NewCallMsg();

        } else if (NewMissedCallPush.ACTION.equals(action)){
            ab = new NewMissedCallMsg();

        } else if (NewMessagePush.ACTION.equals(action)){
            ab = new NewMessageMsg();

        } else if (NewAttentionPush.ACTION.equals(action)){
            ab = new NewAttentionMsg();

        } else if (NewEventPush.ACTION.equals(action)){
            ab = new NewEventMsg();

        } else if (NewOfflineMsgPush.ACTION.equals(action)){
            ab = new NewOfflineMsg();

        } else if (NewMessageOfflinePush.ACTION.equals(action)){
            ab = new NewMessageOfflineMsg();

        } else if (NewMissedCallOfflinePush.ACTION.equals(action)){
            ab = new NewMissedCallOfflineMsg();

        } else if (allowGeneric){
            log.warn("Using generic APN message for: %s", action);
            ab = new ApnMessageBase();

        } else {
            log.warn("Unknown APN message for: %s", action);
            return null;

        }

        ab.setAction(ppush.getAction());
        ab.setTimestamp(ppush.getTime());

        Long expiration = ppush.getExpiration();
        if (expiration != null){
            long expireMs = expiration - ppush.getTime();
            ab.setExpiration(expireMs);
        }

        final String key = ppush.getKey();
        if (key != null && !key.isEmpty()){
            ab.setKey(key);
        }

        return ab;
    }
}
