package org.jivesoftware.openfire.plugin.userService.platformPush;

import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequestMessage;

import java.util.Comparator;

/**
 * Created by dusanklinec on 08.02.16.
 */
public class PushMsgPriorityComparator implements Comparator<MsgPrioritable> {
    @Override
    public int compare(MsgPrioritable t1, MsgPrioritable t2) {
        if (t1 == null && t2 == null){
            return 0;
        }
        if (t1 == null){
            return 1;
        }
        if (t2 == null){
            return -1;
        }

        return t2.getPriority() - t1.getPriority();
    }
}
