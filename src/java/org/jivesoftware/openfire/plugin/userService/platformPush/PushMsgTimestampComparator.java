package org.jivesoftware.openfire.plugin.userService.platformPush;

import java.util.Comparator;

/**
 * Created by dusanklinec on 08.02.16.
 */
public class PushMsgTimestampComparator implements Comparator<MsgTimestampable> {
    @Override
    public int compare(MsgTimestampable t1, MsgTimestampable t2) {
        if (t1 == null && t2 == null){
            return 0;
        }
        if (t1 == null){
            return 1;
        }
        if (t2 == null){
            return -1;
        }

        final long t1t = t1.getTimestamp();
        final long t2t = t2.getTimestamp();
        if (t1t == t2t){
            return 0;
        }

        return t1t > t2t ? -1 : 1;
    }
}
