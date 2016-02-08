package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.PushParser;
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

        ApnMessageBase ab = null;
        final PushRequestMessage pushMsg = PushParser.getMessageByAction(action, allowGeneric);
        if (pushMsg != null){
            ab = pushMsg.getApnMessage(allowGeneric);
        }

        if (ab == null && allowGeneric){
            log.warn("Using generic APN message for: %s", action);
            ab = new ApnMessageBase();

        } else {
            log.warn("Unknown APN message for: %s", action);
            return null;

        }

        ab.setAction(ppush.getAction());
        ab.setTimestamp(ppush.getTime());
        ab.setSourceDbMessage(ppush);

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
