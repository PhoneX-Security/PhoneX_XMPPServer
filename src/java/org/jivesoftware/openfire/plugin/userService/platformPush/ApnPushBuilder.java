package org.jivesoftware.openfire.plugin.userService.platformPush;

import com.notnoop.apns.APNS;
import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.db.DbStrings;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessage;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBase;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBuilder;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewMissedCallOfflineMsg;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.*;
import org.jivesoftware.openfire.plugin.userService.strings.StringsManager;
import org.jivesoftware.openfire.plugin.userService.utils.MiscUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Builder for payload building for the Apple push notifications.
 *
 * Created by dusanklinec on 17.07.15.
 */
public class ApnPushBuilder {
    private static final Logger log = LoggerFactory.getLogger(ApnPushBuilder.class);

    /**
     * Alert formatting string map.
     *   1 .. new message.
     *   2 .. new missed call.
     *   4 .. new active call.
     *   8 .. new event
     *   16 . new attention
     */
    private static final String[] ALERT_KEY_MAP = {
            /* 0 */ null,
            /* 1 */ "L_PHX_PUSH_ALERT_MSG",
            /* 2 */ "L_PHX_PUSH_ALERT_MCALL",
            /* 3 */ "L_PHX_PUSH_ALERT_MCALL_MSG",
            /* 4 */ "L_PHX_PUSH_ALERT_ACALL",
            /* 5 */ "L_PHX_PUSH_ALERT_ACALL_MSG",
            /* 6 */ "L_PHX_PUSH_ALERT_ACALL_MCALL",
            /* 7 */ "L_PHX_PUSH_ALERT_ACALL_MCALL_MSG",
            /* 8 */ "L_PHX_PUSH_ALERT_EVT",
            /* 9 */ "L_PHX_PUSH_ALERT_MSG_EVT",
            /*10 */ "L_PHX_PUSH_ALERT_MCALL_EVT",
            /*11 */ "L_PHX_PUSH_ALERT_MCALL_MSG_EVT",
            /*12 */ "L_PHX_PUSH_ALERT_EVT",
            /*13 */ "L_PHX_PUSH_ALERT_EVT",
            /*14 */ "L_PHX_PUSH_ALERT_EVT",
            /*15 */ "L_PHX_PUSH_ALERT_EVT",
            /*16 */ "L_PHX_PUSH_ALERT_ATT",
    };

    protected String user;
    protected List<TokenConfig> tokens;
    protected List<DbPlatformPush> pushMessagesList;

    // Group messages by action.
    protected final Map<String, List<MsgHolder>> actionMap = new HashMap<String, List<MsgHolder>>();
    // Mapping Action -> Number of notifications in this action group.
    protected final Map<String, Integer> actionBadge = new HashMap<String, Integer>();
    // Mapping Action -> most recent notification (its timestamp) for this action.
    protected final Map<String, Long> actionTime = new HashMap<String, Long>();
    // Mapping Action -> the most recent notification (by its timestamp).
    protected final Map<String, MsgHolder> actionPush = new HashMap<String, MsgHolder>();

    protected String topAlert = null;
    protected int totalUrgency = PushRequestMessage.URGENCY_MIN;
    protected int totalBadge = 0;
    protected String alertStringKey;
    protected String alertString;
    protected String alertStringBody;

    protected JSONObject jsonPayload;
    protected String payload;

    protected String phxPushPayload;
    protected JSONObject jsonPhxPushPayload;

    protected TokenConfig currentToken;
    protected StringsManager strings;

    // Alert tags
    boolean newMsg = false;
    boolean newMissed = false;
    boolean newCall = false;
    boolean newEvent = false;
    boolean newAttention = false;
    boolean newOffline = false;

    /**
     * Grouping, processing of the input messages.
     */
    protected void preprocess(){
        // Reset old badges count.
        totalBadge = 0;
        actionBadge.clear();
        for(DbPlatformPush ppush : pushMessagesList){
            final String action = ppush.getAction();
            List<MsgHolder> lstToUse = null;

            if (!actionMap.containsKey(action)){
                lstToUse = new ArrayList<MsgHolder>();
                actionMap.put(action, lstToUse);

            } else {
                lstToUse = actionMap.get(action);

            }

            final MsgHolder msgHolder = new MsgHolder();
            msgHolder.setDbPush(ppush);
            msgHolder.setApnPush(ApnMessageBuilder.buildMessageFromDb(ppush, true));
            msgHolder.setReqPush(PushParser.getMessageByAction(ppush.getAction(), true));

            lstToUse.add(msgHolder);

            // Compute badge number for given action.
            Integer curNum = actionBadge.get(action);
            actionBadge.put(action, curNum == null ? 1 : curNum + 1);
            totalBadge += 1;

            // Most recent timestamp for given action.
            Long lastTstamp = actionTime.get(action);
            if (lastTstamp == null || lastTstamp < ppush.getTime()){
                actionTime.put(action, ppush.getTime());
                actionPush.put(action, msgHolder);
            }

            // Overall urgency, maximum number.
            final PushRequestMessage msg = PushParser.getMessageByAction(action, false);
            if (msg != null){
                final int curUrg = msg.getUrgencyType();
                if (curUrg > totalUrgency){
                    topAlert = msg.getAlertStringKey();
                    totalUrgency = curUrg;
                }
            }
        }
    }

    /**
     * Sets boolean alert tags according to the cached actions.
     */
    public void processAlertTags(){
        newMsg    = actionPush.containsKey(NewMessagePush.ACTION);
        newMissed = actionPush.containsKey(NewMissedCallPush.ACTION);
        newCall   = actionPush.containsKey(NewActiveCallPush.ACTION);
        newEvent  = actionPush.containsKey(NewEventPush.ACTION);
        newAttention = actionPush.containsKey(NewAttentionPush.ACTION);
        newOffline = actionPush.containsKey(NewOfflineMsgPush.ACTION);

        // Offline events.
        newMissed |= actionPush.containsKey(NewMissedCallOfflinePush.ACTION);
        newMsg    |= actionPush.containsKey(NewMessageOfflinePush.ACTION);
    }

    /**
     * Attempts to construct alert string key from all messages in the current push bulk.
     */
    public void buildAlertStringKey(){
        processAlertTags();

        int alertIdx = newMsg       ? 1 : 0;
        alertIdx    |= newMissed    ? 1 << 1: 0;
        alertIdx    |= newCall      ? 1 << 2: 0;
        alertIdx    |= newEvent     ? 1 << 3: 0;
        alertIdx    |= newAttention ? 1 << 4: 0;
        alertIdx    |= newOffline   ? 1 << 5: 0;

        if (alertIdx >= ALERT_KEY_MAP.length){
            try {
                final int highestBit = MiscUtils.lg2(alertIdx);
                if (highestBit < ALERT_KEY_MAP.length) {
                    alertStringKey = ALERT_KEY_MAP[highestBit];
                }

            } catch(Exception e){
                log.error("Exception in alert string building", e);
            }

        } else {
            alertStringKey = ALERT_KEY_MAP[alertIdx];

        }
    }

    /**
     * BBuilds string description
     */
    public void buildAlertString(){
        processAlertTags();

        // A new list, sorted by priority and time stamp.
        List<MsgHolder> msgsByPriority = new ArrayList<MsgHolder>(actionPush.values());
        List<MsgHolder> msgsByTimestamp = new ArrayList<MsgHolder>(actionPush.values());
        Collections.sort(msgsByPriority, new PushMsgPriorityComparator());
        Collections.sort(msgsByTimestamp, new PushMsgTimestampComparator());

        // Prepare default title, just in case.
        final List<Locale> locales = StringsManager.getLocales(currentToken.getLangs());
        buildDefaultAlertString(locales);

        // Building alert tile -> default if empty list.
        if (actionPush.isEmpty()){
            return;
        }

        // At least one element, get the most recent one. If does not have entitlement for title
        // presence, move to next.
        for(MsgHolder cMsg : msgsByTimestamp){
            if (cMsg == null
                    || cMsg.getReqPush() == null
                    || cMsg.getReqPush().getAlertStringKey() == null)
            {
                continue;
            }

            final DbStrings newActivityString = strings.loadStringCached(cMsg.getReqPush().getAlertStringKey(), locales);
            if (newActivityString == null){
                log.warn("Empty translation for " + cMsg.getReqPush().getAlertStringKey());
                continue;
            }

            alertString = newActivityString.getValue();
        }

        // Build summary:
        final DbStrings summaryString = strings.loadStringCached("PUSH_SUMMARY", locales);
        if (summaryString == null){
            return;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(alertString);
        sb.append(". ");
        sb.append(summaryString.getValue());

        final List<String> summaryList = new LinkedList<String>();
        for(MsgHolder cMsg : msgsByPriority){
            if (cMsg == null
                    || cMsg.getReqPush() == null
                    || cMsg.getReqPush().getAlertSummaryKey() == null
                    || cMsg.getReqPush().getAction() == null)
            {
                continue;
            }

            final Integer badgeCount = actionBadge.get(cMsg.getReqPush().getAction());
            if (badgeCount == null || badgeCount == 0){
                continue;
            }

            final DbStrings summarySubString = strings.loadStringCached(cMsg.getReqPush().getAlertSummaryKey(), locales);
            if (summarySubString == null){
                continue;
            }

            summaryList.add(summarySubString.getValue() + " (" + badgeCount + ")");
        }

        // If summary is empty, do not fill string "Summary:" with nothing, it looks dumb.
        if (summaryList.isEmpty()){
            return;
        }

        sb.append(MiscUtils.join(summaryList, ", "));
        alertStringBody = sb.toString();
    }

    /**
     * Default alert string for push notification.
     * Called when more particular title & body cannot be built.
     */
    public void buildDefaultAlertString(List<Locale> locales){
        alertString = "New activity on PhoneX";
        if (currentToken == null || strings == null){
            return;
        }

        final DbStrings newActivityString = strings.loadStringCached("PUSH_NEW_ACTIVITY", locales);
        if (newActivityString == null){
            return;
        }

        alertString = newActivityString.getValue();
        alertStringBody = newActivityString.getValue();
    }

    public void build() throws JSONException {
        buildForToken(null);
    }

    /**
     * Build apple push.
     */
    public void buildForToken(TokenConfig token) throws JSONException {
        currentToken = token;

        // Group, categorize, compute priorities.
        preprocess();

        // Generate total alert name.
        buildAlertStringKey();
        buildAlertString();

        // Build custom JSON notification.
        JSONObject root = new JSONObject();
        JSONArray pushArr = new JSONArray();
        for (Map.Entry<String, MsgHolder> eset : actionPush.entrySet()) {
            final String action = eset.getKey();
            final ApnMessage apnMessage = eset.getValue().getApnPush();
            if (apnMessage == null){
                log.warn("Null APN message");
                continue;
            }

            // Set badge number for the notification - how many of them.
            apnMessage.setBadge(actionBadge.get(action));
            final JSONObject json = apnMessage.apnToJson();
            pushArr.put(json);
        }

        root.put("msg", pushArr);
        jsonPhxPushPayload = root;
        phxPushPayload = root.toString();

        // Build general APN payload.
        final String tmpPayload = APNS.newPayload()
                .badge(totalBadge)
                .alertBody(alertStringBody)
                .alertTitle(alertString)
                .build();

        // Parse general payload, build JSON.
        jsonPayload = new JSONObject(tmpPayload);
        jsonPayload.put("phx", root);
        payload = jsonPayload.toString();
    }

    public String getUser() {
        return user;
    }

    public ApnPushBuilder setUser(String user) {
        this.user = user;
        return this;
    }

    public List<TokenConfig> getTokens() {
        return tokens;
    }

    public ApnPushBuilder setTokens(List<TokenConfig> tokens) {
        this.tokens = tokens;
        return this;
    }

    public List<DbPlatformPush> getPushMessagesList() {
        return pushMessagesList;
    }

    public ApnPushBuilder setPushMessagesList(List<DbPlatformPush> pushMessagesList) {
        this.pushMessagesList = pushMessagesList;
        return this;
    }

    public String getAlertStringKey() {
        return alertStringKey;
    }

    public int getTotalBadge() {
        return totalBadge;
    }

    public int getTotalUrgency() {
        return totalUrgency;
    }

    public String getTopAlert() {
        return topAlert;
    }

    public JSONObject getJsonPayload() {
        return jsonPayload;
    }

    public String getPayload() {
        return payload;
    }

    public String getPhxPushPayload() {
        return phxPushPayload;
    }

    public JSONObject getJsonPhxPushPayload() {
        return jsonPhxPushPayload;
    }

    public StringsManager getStrings() {
        return strings;
    }

    public ApnPushBuilder setStrings(StringsManager strings) {
        this.strings = strings;
        return this;
    }

    /**
     * Holds all representation of the message for better manipulation.
     */
    private static class MsgHolder implements MsgPrioritable, MsgTimestampable {
        private DbPlatformPush dbPush;
        private ApnMessage apnPush;
        private PushRequestMessage reqPush;

        @Override
        public int getPriority() {
            if (reqPush != null){
                return reqPush.getPriority();
            }
            if (dbPush != null){
                return dbPush.getPriority();
            }

            return 0;
        }

        @Override
        public long getTimestamp() {
            if (apnPush != null){
                return apnPush.getTimestamp();
            }
            if (dbPush != null){
                return dbPush.getTime();
            }
            if (reqPush != null){
                return reqPush.getTstamp();
            }

            return 0;
        }

        public DbPlatformPush getDbPush() {
            return dbPush;
        }

        public void setDbPush(DbPlatformPush dbPush) {
            this.dbPush = dbPush;
        }

        public ApnMessage getApnPush() {
            return apnPush;
        }

        public void setApnPush(ApnMessage apnPush) {
            this.apnPush = apnPush;
        }

        public PushRequestMessage getReqPush() {
            return reqPush;
        }

        public void setReqPush(PushRequestMessage reqPush) {
            this.reqPush = reqPush;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MsgHolder msgHolder = (MsgHolder) o;

            if (dbPush != null ? !dbPush.equals(msgHolder.dbPush) : msgHolder.dbPush != null) return false;
            if (apnPush != null ? !apnPush.equals(msgHolder.apnPush) : msgHolder.apnPush != null) return false;
            return !(reqPush != null ? !reqPush.equals(msgHolder.reqPush) : msgHolder.reqPush != null);

        }

        @Override
        public int hashCode() {
            int result = dbPush != null ? dbPush.hashCode() : 0;
            result = 31 * result + (apnPush != null ? apnPush.hashCode() : 0);
            result = 31 * result + (reqPush != null ? reqPush.hashCode() : 0);
            return result;
        }
    }
}
