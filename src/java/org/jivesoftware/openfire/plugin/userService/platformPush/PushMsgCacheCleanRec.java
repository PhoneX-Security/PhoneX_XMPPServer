package org.jivesoftware.openfire.plugin.userService.platformPush;

/**
 * Cache record for push message request queue cleaning.
 * Stores (timestamp, recordsAhead) for the user-action key.
 * If there is update with cache recordsAhead > limit, clean query is triggered for given user.
 */
public class PushMsgCacheCleanRec {
    private long timestamp;
    private int recordsAhead = 0;
    private String user;
    private String action;

    private final Object mutex = new Object();

    public static PushMsgCacheCleanRec copyFrom(PushMsgCacheCleanRec source){
        final PushMsgCacheCleanRec clone = new PushMsgCacheCleanRec();
        clone.setTimestamp(source.getTimestamp());
        clone.setRecordsAhead(source.getRecordsAhead());
        clone.setUser(source.getUser());
        clone.setAction(source.getAction());
        return clone;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getRecordsAhead() {
        return recordsAhead;
    }

    public void setRecordsAhead(int recordsAhead) {
        this.recordsAhead = recordsAhead;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Object getMutex() {
        return mutex;
    }
}
