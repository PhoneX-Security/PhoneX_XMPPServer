package org.jivesoftware.openfire.plugin.userService.push;

/**
 * Record returned after merging with ACK queue.
 */
public class AckMergeRecord {
    protected boolean hasChanged = false;
    protected boolean hasMerged = false;
    protected String oldPacketId = null;
    protected PushSendRecord newRecord = null;

    // Optimization - not merged singleton.
    public static final AckMergeRecord NOT_MERGED = new AckMergeRecord(false);

    public AckMergeRecord() {
    }

    public AckMergeRecord(boolean hasMerged) {
        this.hasMerged = hasMerged;
    }

    public AckMergeRecord(boolean hasMerged, String oldPacketId, PushSendRecord newRecord) {
        this.hasMerged = hasMerged;
        this.oldPacketId = oldPacketId;
        this.newRecord = newRecord;
    }

    public boolean wasChanged() {
        return hasChanged;
    }

    public void setHasChanged(boolean hasChanged) {
        this.hasChanged = hasChanged;
    }

    public boolean wasMerged() {
        return hasMerged;
    }

    public void setHasMerged(boolean hasMerged) {
        this.hasMerged = hasMerged;
    }

    public String getOldPacketId() {
        return oldPacketId;
    }

    public void setOldPacketId(String oldPacketId) {
        this.oldPacketId = oldPacketId;
    }

    public PushSendRecord getNewRecord() {
        return newRecord;
    }

    public void setNewRecord(PushSendRecord newRecord) {
        this.newRecord = newRecord;
    }
}
