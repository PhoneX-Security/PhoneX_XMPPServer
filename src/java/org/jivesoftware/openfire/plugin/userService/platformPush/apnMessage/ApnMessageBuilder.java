package org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage;

import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.*;

/**
 * Created by dusanklinec on 17.07.15.
 */
public class ApnMessageBuilder {
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

        } else if (allowGeneric){
            ab = new ApnMessageBase();

        } else {
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
