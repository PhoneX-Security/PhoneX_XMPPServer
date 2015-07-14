package org.jivesoftware.openfire.plugin.userService.clientState;

import org.xmpp.packet.JID;

/**
 * Holding information about last activity for the given user.
 *
 * Created by dusanklinec on 09.07.15.
 */
public class ActivityRecord {
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_INACTIVE = 2;
    public static final int STATE_OFFLINE = 3;

    public static final String FIELD_USER = "ofUser";
    public static final String FIELD_RESOURCE = "ofResource";
    public static final String FIELD_ACT_TIME = "ofActTime";
    public static final String FIELD_LAST_STATUS = "ofLastStatus";

    protected JID user;
    protected long lastActiveMilli;
    protected int lastState = STATE_ACTIVE;

    /**
     * Sentinel record = null record. Sentinel is stored for the user with no last activity record so
     * database is not queried each time for missing record, cache is used for response.
     */
    protected boolean isSentinel = false;

    public ActivityRecord() {
    }

    public ActivityRecord(boolean isSentinel) {
        this.isSentinel = isSentinel;
    }

    public ActivityRecord(JID user, boolean isSentinel) {
        this.user = user;
        this.isSentinel = isSentinel;
    }

    public ActivityRecord(JID user, long lastActiveMilli, int lastState) {
        this.user = user;
        this.lastActiveMilli = lastActiveMilli;
        this.lastState = lastState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActivityRecord that = (ActivityRecord) o;

        if (lastActiveMilli != that.lastActiveMilli) return false;
        if (lastState != that.lastState) return false;
        if (isSentinel != that.isSentinel) return false;
        return !(user != null ? !user.equals(that.user) : that.user != null);

    }

    @Override
    public int hashCode() {
        int result = user != null ? user.hashCode() : 0;
        result = 31 * result + (int) (lastActiveMilli ^ (lastActiveMilli >>> 32));
        result = 31 * result + lastState;
        result = 31 * result + (isSentinel ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ActivityRecord{" +
                "user=" + user +
                ", lastActiveMilli=" + lastActiveMilli +
                ", lastState=" + lastState +
                ", isSentinel=" + isSentinel +
                '}';
    }

    public JID getUser() {
        return user;
    }

    public void setUser(JID user) {
        this.user = user;
    }

    public long getLastActiveMilli() {
        return lastActiveMilli;
    }

    public void setLastActiveMilli(long lastActiveMilli) {
        this.lastActiveMilli = lastActiveMilli;
    }

    public int getLastState() {
        return lastState;
    }

    public void setLastState(int lastState) {
        this.lastState = lastState;
    }

    public boolean isSentinel() {
        return isSentinel;
    }

    public void setIsSentinel(boolean isSentinel) {
        this.isSentinel = isSentinel;
    }
}
