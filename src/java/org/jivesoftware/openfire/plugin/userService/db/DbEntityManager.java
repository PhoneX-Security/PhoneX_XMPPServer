package org.jivesoftware.openfire.plugin.userService.db;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.plugin.userService.clientState.ActivityRecord;
import org.jivesoftware.openfire.plugin.userService.platformPush.TokenConfig;
import org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage.PushAck;
import org.jivesoftware.openfire.plugin.userService.platformPush.ackMessage.PushAckMessage;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequest;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by dusanklinec on 12.03.15.
 */
public class DbEntityManager {
    private static final Logger log = LoggerFactory.getLogger(DbEntityManager.class);
    private static final String SQL_FETCH_MESSAGES_USR_ACTION="SELECT * FROM ofPushMessages WHERE forUser=? AND msgAction=?";
    private static final String SQL_FETCH_MESSAGES_USR="SELECT * FROM ofPushMessages WHERE forUser=?";
    private static final String SQL_DELETE_MESSAGES_FMT="DELETE FROM ofPushMessages WHERE msgID IN(%s)";
    private static final String SQL_DELETE_MESSAGES_USR="DELETE FROM ofPushMessages WHERE msgAction=? AND forUser=?";
    private static final String SQL_EXPIRE_PUSH_REQ="DELETE FROM ofPhxPlatformMessages WHERE ofMsgExpire IS NOT NULL AND ofMsgExpire<=NOW()";
    private static final String SQL_EXPIRE_PUSH_REQ_USR="DELETE FROM ofPhxPlatformMessages WHERE ofMsgExpire IS NOT NULL AND ofMsgExpire<=NOW() AND ofForUser=?";
    private static final String SQL_DELETE_PUSH_REQ_ID_FMT="DELETE FROM ofPhxPlatformMessages WHERE ofMsgId IN(%s)";
    private static final String SQL_DELETE_PUSH_REQ_USR_KEY_FMT="DELETE FROM ofPhxPlatformMessages WHERE ofForUser=? AND ofMsgKey IS NOT NULL AND ofMsgKey IN(%s)";
    private static final String SQL_DELETE_PUSH_REQ_USR_ACTTIME_FMT="DELETE FROM ofPhxPlatformMessages WHERE ofForUser=? AND %s";
    private static final String SQL_DELETE_PUSH_REQ_USR_ACTION_TIME="DELETE FROM ofPhxPlatformMessages WHERE ofForUser=? AND ofMsgAction=? AND ofMsgTime<?";
    private static final String SQL_FETCH_TOKENS_USERS_FMT="SELECT * FROM ofPushTokenApple WHERE ofUser IN (%s)";
    private static final String SQL_FETCH_PUSH_REQ_USERS_FMT="SELECT * FROM ofPhxPlatformMessages WHERE ofMsgExpire > NOW() AND ofForUser IN (%s)";
    private static final String SQL_DELETE_TOKENS_FMT="DELETE FROM ofPushTokenApple WHERE ofDeviceToken IN (%s)";
    private static final String SQL_CLEAN_PUSH_CREATE_TEMP_TABLE_FMT ="CREATE TEMPORARY TABLE IF NOT EXISTS cleanPlatformMessages AS (\n" +
            "SELECT tt.ofMsgId\n" +
            "FROM \n" +
            "      ( SELECT DISTINCT ofForUser, ofMsgAction             \n" +
            "        FROM ofPhxPlatformMessages                    \n" +
            "      ) AS du                          \n" +
            "  JOIN\n" +
            "      ofPhxPlatformMessages AS tt\n" +
            "    ON  tt.ofForUser = du.ofForUser AND tt.ofMsgAction = du.ofMsgAction\n" +
            "    AND tt.ofMsgTime <\n" +
            "        ( SELECT ofMsgTime AS ts\n" +
            "          FROM ofPhxPlatformMessages\n" +
            "          WHERE ofForUser = du.ofForUser AND ofMsgAction=du.ofMsgAction\n" +
            "          ORDER BY ts DESC\n" +
            "          LIMIT 1 OFFSET %d\n" +
            "        ))";

    private static final String SQL_CLEAN_PUSH_CREATE_TEMP_TABLE_USER_ACTION_FMT ="CREATE TEMPORARY TABLE IF NOT EXISTS cleanPlatformMessages AS (\n" +
            "SELECT tt.ofMsgId\n" +
            "FROM \n" +
            "      ( SELECT DISTINCT ofForUser, ofMsgAction             \n" +
            "        FROM ofPhxPlatformMessages " +
            "        WHERE ofForUser=? AND ofMsgAction=?                \n" +
            "      ) AS du                          \n" +
            "  JOIN\n" +
            "      ofPhxPlatformMessages AS tt\n" +
            "    ON  tt.ofForUser = du.ofForUser AND tt.ofMsgAction = du.ofMsgAction\n" +
            "    AND tt.ofMsgTime <\n" +
            "        ( SELECT ofMsgTime AS ts\n" +
            "          FROM ofPhxPlatformMessages\n" +
            "          WHERE ofForUser = du.ofForUser AND ofMsgAction=du.ofMsgAction\n" +
            "          ORDER BY ts DESC\n" +
            "          LIMIT 1 OFFSET %d\n" +
            "        ))";

    private static final String SQL_CLEAN_PUSH_DELETE_BY_TEMP_TABLE = "DELETE FROM ofPhxPlatformMessages WHERE ofMsgId IN (SELECT ofMsgId FROM cleanPlatformMessages)";

    /**
     * Helper method for timestamp extraction from NULL field.
     * @param rs
     * @param colname
     * @return
     * @throws SQLException
     */
    public static Long getTimeStamp(ResultSet rs, String colname) throws SQLException {
        final Timestamp tstamp = rs.getTimestamp(colname);
        return tstamp == null ? null : tstamp.getTime();
    }

    /**
     * Builds DbPushMessage from result set.
     * @param rs
     * @return
     */
    public static DbPushMessage msgFromRes(ResultSet rs){
        if (rs == null){
            return null;
        }

        DbPushMessage msg = null;
        try {
            msg = new DbPushMessage();
            msg.setId(rs.getLong(DbPushMessage.FIELD_ID));
            msg.setAction(rs.getString(DbPushMessage.FIELD_ACTION));
            msg.setTstamp(getTimeStamp(rs, DbPushMessage.FIELD_TIME));
            msg.setExpireTstamp(getTimeStamp(rs, DbPushMessage.FIELD_EXPIRE_TIME));
            msg.setToUser(rs.getString(DbPushMessage.FIELD_USER));
            msg.setToResource(rs.getString(DbPushMessage.FIELD_RESOURCE));
            msg.setDurable(rs.getBoolean(DbPushMessage.FIELD_IS_DURABLE));
            msg.setUnique(rs.getBoolean(DbPushMessage.FIELD_IS_UNIQUE));
            msg.setAuxData(rs.getString(DbPushMessage.FIELD_DATA));
            msg.setAux1(rs.getString(DbPushMessage.FIELD_AUX1));
            msg.setAux2(rs.getString(DbPushMessage.FIELD_AUX2));

        } catch(Exception e){
            log.error("Exception in loading DbPushMessage from RS", e);
            msg = null;
        }

        return msg;
    }

    /**
     * Creates a token object from the result set.
     * @param rs
     * @return
     */
    public static TokenConfig tokenFromRes(ResultSet rs){
        if (rs == null){
            return null;
        }

        TokenConfig tkn = null;
        try {
            tkn = new TokenConfig();
            final String user = rs.getString(DbTokenConfig.FIELD_USER);
            final String resource = rs.getString(DbTokenConfig.FIELD_RESOURCE);
            final JID jid = new JID(user + "/" + resource);

            tkn.setUser(jid);
            tkn.setToken(rs.getString(DbTokenConfig.FIELD_TOKEN));
            tkn.setFakeUdid(rs.getString(DbTokenConfig.FIELD_FAKE_UDID));
            tkn.setVersion(rs.getString(DbTokenConfig.FIELD_VERSION));
            tkn.setAppVersion(rs.getString(DbTokenConfig.FIELD_APP_VERSION));
            tkn.setOsVersion(rs.getString(DbTokenConfig.FIELD_OS_VERSION));
            tkn.setLangsField(rs.getString(DbTokenConfig.FIELD_LANGS));
            tkn.setDebug(rs.getBoolean(DbTokenConfig.FIELD_DEBUG));
            tkn.setAuxJson(rs.getString(DbTokenConfig.FIELD_AUX_JSON));

        } catch(Exception e){
            log.error("Exception in loading DbPushMessage from RS", e);
            tkn = null;
        }

        return tkn;
    }

    /**
     * Builds DbPushDelivery from result set.
     * @param rs
     * @return
     */
    public static DbPushDelivery deliveryFromRes(ResultSet rs){
        if (rs == null){
            return null;
        }

        DbPushDelivery msg = null;
        try {
            msg = new DbPushDelivery();
            msg.setId(rs.getLong(DbPushDelivery.FIELD_ID));
            msg.setPushMessageId(rs.getLong(DbPushDelivery.FIELD_MSG_ID));
            msg.setTstamp(getTimeStamp(rs, DbPushDelivery.FIELD_TIME));
            msg.setUser(rs.getString(DbPushDelivery.FIELD_USER));
            msg.setResource(rs.getString(DbPushDelivery.FIELD_RESOURCE));
            msg.setStatus(rs.getInt(DbPushDelivery.FIELD_STATUS));
        } catch(Exception e){
            log.error("Exception in loading DbPushMessage from RS", e);
            msg = null;
        }

        return msg;
    }

    /**
     * Builds ActivityRecord from result set.
     * @param rs
     * @return
     */
    public static ActivityRecord activityRecordFromRes(ResultSet rs){
        if (rs == null){
            return null;
        }

        ActivityRecord ar = null;
        try {
            final String user = rs.getString(ActivityRecord.FIELD_USER);
            final String resource = rs.getString(ActivityRecord.FIELD_RESOURCE);
            final JID jid = new JID(user + "/" + resource);

            ar = new ActivityRecord();
            ar.setUser(jid);
            ar.setLastActiveMilli(getTimeStamp(rs, ActivityRecord.FIELD_ACT_TIME));
            ar.setLastState(rs.getInt(ActivityRecord.FIELD_LAST_STATUS));

        } catch(Exception e){
            log.error("Exception in loading DbPushMessage from RS", e);
            ar = null;
        }

        return ar;
    }

    /**
     * Returns all bookmarks.
     * Action is optional.
     *
     * @return the collection of bookmarks.
     */
    public static Collection<DbPushMessage> getPushByUserAndAction(String user, String action) {
        List<DbPushMessage> msgs = new ArrayList<DbPushMessage>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(action == null ? SQL_FETCH_MESSAGES_USR : SQL_FETCH_MESSAGES_USR_ACTION);
            pstmt.setString(1, user);
            if (action != null) {
                pstmt.setString(2, action);
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    DbPushMessage msg = msgFromRes(rs);
                    msgs.add(msg);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return msgs;
    }

    /**
     * Deletes given list of messages with given IDs.
     * @param ids
     */
    public static int deleteMessages(List<Long> ids){
        if (ids == null || ids.size() == 0){
            return 0;
        }

        int affected = 0;
        final int cnt = ids.size();
        final String query = String.format(SQL_DELETE_MESSAGES_FMT, genPlaceholders(cnt));

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(query);

            int ctr = 0;
            for (Long id : ids) {
                pstmt.setLong(++ctr, id);
            }

            affected = pstmt.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeConnection(con);
        }

        return affected;
    }

    /**
     * Deletes messages with given user & action.
     * @param user
     * @param action
     */
    public static void deleteMessages(String user, String action){
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SQL_DELETE_MESSAGES_USR);
            pstmt.setString(1, action);
            pstmt.setString(2, user);
            rs = pstmt.executeQuery();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Stores message to database.
     * @param msg
     * @param keepNewestOnly
     */
    public static Long persistDbMessage(DbPushMessage msg, boolean keepNewestOnly){
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmtDelete = null;
        ResultSet rs = null;

        final String q  = "INSERT INTO ofPushMessages VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final String dq = "DELETE FROM ofPushMessages WHERE forUser=? AND forResource=? AND msgAction=? AND msgTime <= ?";
        try {
            con = DbConnectionManager.getConnection();
            con.setAutoCommit(false);

            // Delete older of same type
            if (keepNewestOnly) {
                pstmtDelete = con.prepareStatement(dq);
                pstmtDelete.setString(1, msg.getToUser());
                pstmtDelete.setString(2, msg.getToResource() == null ? "" : msg.getToResource());
                pstmtDelete.setString(3, msg.getAction());
                pstmtDelete.setTimestamp(4, new Timestamp(msg.getTstamp()));
                pstmtDelete.executeUpdate();
            }

            // Insert new one.
            pstmt = con.prepareStatement(q, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, msg.getAction());
            pstmt.setTimestamp(2, new Timestamp(msg.getTstamp()));
            pstmt.setTimestamp(3, null);
            pstmt.setString(4, msg.getToUser());
            pstmt.setString   (5,  msg.getToResource() == null ? "" : msg.getToResource());
            pstmt.setBoolean(6, msg.isDurable());
            pstmt.setBoolean(7, msg.isUnique());
            pstmt.setString   (8,  msg.getAux1());
            pstmt.setString(9, msg.getAux2());
            pstmt.setString(10, msg.getAuxData());

            pstmt.executeUpdate();
            con.commit();

            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
            rollback(con);
            return null;
        }
        finally {
            DbConnectionManager.closeStatement(pstmtDelete);
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return null;
    }

    /**
     * Stores message ACK to database.
     * @param msg
     */
    public static Long persistDbAck(DbPushDelivery msg){
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmtDelete = null;
        ResultSet rs = null;

        final String q  = "INSERT INTO ofPushDelivery VALUES (NULL, ?, ?, ?, ?, ?)";
        final String dq = "DELETE FROM ofPushDelivery WHERE msgId=? AND forUser=? AND forResource=?";
        try {
            con = DbConnectionManager.getConnection();
            con.setAutoCommit(false);

            pstmtDelete = con.prepareStatement(dq);
            pstmtDelete.setLong(1, msg.getPushMessageId());
            pstmtDelete.setString(2, msg.getUser());
            pstmtDelete.setString(3, msg.getResource() == null ? "" : msg.getResource());
            pstmtDelete.executeUpdate();

            pstmt = con.prepareStatement(q, Statement.RETURN_GENERATED_KEYS);
            pstmt.setLong(1, msg.getPushMessageId());
            pstmt.setTimestamp(2, new Timestamp(msg.getTstamp()));
            pstmt.setString   (3,  msg.getUser());
            pstmt.setString(4, msg.getResource() == null ? "" : msg.getResource());
            pstmt.setInt(5, msg.getStatus());

            pstmt.executeUpdate();
            con.commit();

            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        catch (SQLException e) {
            log.info(e.getMessage(), e);
            rollback(con);
        }
        finally {
            DbConnectionManager.closeStatement(pstmtDelete);
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return null;
    }

    /**
     * Stores message ACK to database.
     * @param tokenConfig
     */
    public static void persistAppleTokenConfig(TokenConfig tokenConfig){
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmtDelete = null;

        final String q  = "INSERT INTO ofPushTokenApple VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final String dq = "DELETE FROM ofPushTokenApple WHERE ofUser=? AND ofResource=?";
        try {
            final JID user = tokenConfig.getUser();

            con = DbConnectionManager.getConnection();
            con.setAutoCommit(false);

            pstmtDelete = con.prepareStatement(dq);
            pstmtDelete.setString(1, user.toBareJID());
            pstmtDelete.setString(2, user.getResource());
            pstmtDelete.executeUpdate();

            pstmt = con.prepareStatement(q);
            pstmt.setString(1, user.asBareJID().toString());
            pstmt.setString(2, user.getResource());
            pstmt.setString(3, tokenConfig.getToken());
            pstmt.setString(4, tokenConfig.getFakeUdid());
            pstmt.setString   (5, tokenConfig.getVersion());
            pstmt.setString(6, tokenConfig.getAppVersion());
            pstmt.setString(7, tokenConfig.getOsVersion());
            pstmt.setString   (8, tokenConfig.getLangList());
            pstmt.setBoolean(9, tokenConfig.getDebug());
            pstmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            pstmt.setString(11, tokenConfig.getAuxJson());

            pstmt.executeUpdate();
            con.commit();
        }
        catch (SQLException e) {
            log.info(e.getMessage(), e);
            rollback(con);
        }
        finally {
            DbConnectionManager.closeStatement(pstmtDelete);
            DbConnectionManager.closeConnection(null, pstmt, con);
        }
    }

    /**
     * Deletes inactive tokens from the database.
     * @param tokens
     * @return
     */
    public static int deleteTokens(Collection<String> tokens){
        if (tokens == null || tokens.size() == 0){
            return 0;
        }

        int affected = 0;
        final int cnt = tokens.size();
        final String query = String.format(SQL_DELETE_TOKENS_FMT, genPlaceholders(cnt));

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(query);

            int ctr = 0;
            for (String tokenId : tokens) {
                pstmt.setString(++ctr, tokenId);
            }

            affected = pstmt.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeConnection(con);
        }

        return affected;
    }

    /**
     * Loads list of all tokens related to the given users.
     * @param users
     * @return
     */
    public static List<TokenConfig> loadTokens(Collection<String> users){
        // TODO: use DB loader for this as we have on Android as the list may be large and SQL select would fail.
        List<TokenConfig> tokens = new ArrayList<TokenConfig>();
        if (users == null || users.isEmpty()){
            return tokens;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        final int cnt = users.size();
        final String query = String.format(SQL_FETCH_TOKENS_USERS_FMT, genPlaceholders(cnt));
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(query);
            int ctr = 0;
            for (String usr : users) {
                pstmt.setString(++ctr, usr);
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    TokenConfig token = tokenFromRes(rs);
                    tokens.add(token);

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return tokens;
    }

    /**
     * Stores last activity record to the database.
     * @param ar ActivityRecord to store to the database.
     */
    public static void persistLastActivity(ActivityRecord ar){
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmtDelete = null;

        final String q  = "INSERT INTO ofPhxLastActivity VALUES (?, ?, ?, ?)";
        final String dq = "DELETE FROM ofPhxLastActivity WHERE ofUser=? AND ofResource=?";
        try {
            final JID user = ar.getUser();

            con = DbConnectionManager.getConnection();
            con.setAutoCommit(false);

            pstmtDelete = con.prepareStatement(dq);
            pstmtDelete.setString(1, user.toBareJID());
            pstmtDelete.setString(2, user.getResource());
            pstmtDelete.executeUpdate();

            pstmt = con.prepareStatement(q);
            pstmt.setString   (1, user.asBareJID().toString());
            pstmt.setString(2, user.getResource());
            pstmt.setTimestamp(3, new Timestamp(ar.getLastActiveMilli()));
            pstmt.setInt(4, ar.getLastState());
            pstmt.executeUpdate();
            con.commit();
        }
        catch (SQLException e) {
            log.info(e.getMessage(), e);
            rollback(con);
        }
        finally {
            DbConnectionManager.closeStatement(pstmtDelete);
            DbConnectionManager.closeConnection(null, pstmt, con);
        }
    }

    /**
     * Loads last activity for the given user from database.
     * Returns null if no such record was found.
     *
     * @param user
     * @return
     */
    public static ActivityRecord getLastActivity(JID user){
        List<DbPushMessage> msgs = new ArrayList<DbPushMessage>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("SELECT * FROM ofPhxLastActivity WHERE ofUser=? AND ofResource=? LIMIT 1");
            pstmt.setString(1, user.asBareJID().toString());
            pstmt.setString(2, user.getResource());

            rs = pstmt.executeQuery();
            if (!rs.first()) {
                return null;
            }

            try {
                return activityRecordFromRes(rs);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return null;
    }

    /**
     * Loads all push message requests from the database where toUser is in the given set.
     * Expired messages are ignored.
     *
     * @param toUser
     * @return
     */
    public static List<DbPlatformPush> loadPushRequestMessages(Collection<String> toUser){
        // TODO: use DB loader for this as we have on Android as the list may be large and SQL select would fail.
        List<DbPlatformPush> requests = new ArrayList<DbPlatformPush>();
        if (toUser == null || toUser.isEmpty()){
            return requests;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        final int cnt = toUser.size();
        final String query = String.format(SQL_FETCH_PUSH_REQ_USERS_FMT, genPlaceholders(cnt));
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(query);
            int ctr = 0;
            for (String usr : toUser) {
                pstmt.setString(++ctr, usr);
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    DbPlatformPush ppush = DbPlatformPush.createFromResultSet(rs);
                    requests.add(ppush);

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return requests;
    }

    /**
     * Persists current push message request to the database.
     * Message is considered as a generic one, behaviour is defined by the properties of the message (type, uniqueness...).
     *
     * @param parent
     * @param req
     */
    public static boolean persistNewPushRequest(PushRequest parent, PushRequestMessage req){
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmtDelete = null;

        // Insert query, full row.
        final String q  = "INSERT INTO ofPhxPlatformMessages VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // Delete msg by its key
        final String delKey = "DELETE FROM ofPhxPlatformMessages WHERE ofFromUser=? AND ofFromResource=? AND ofMsgKey=?";
        // Delete msg by its action
        final String delAct = "DELETE FROM ofPhxPlatformMessages WHERE ofFromUser=? AND ofFromResource=? AND ofMsgAction=?";
        // Delete msg by its action & destination.
        final String delActUsr = "DELETE FROM ofPhxPlatformMessages WHERE ofFromUser=? AND ofFromResource=? AND ofMsgAction=? AND ofForUser=?";
        try {
            final JID user = parent.getFromUser();
            final String key = req.getKey();

            con = DbConnectionManager.getConnection();
            con.setAutoCommit(false);

            // Do not delete non-unique messages once it is not a cancellation.
            // If the message request is cancellation, delete corresponding request.
            if (req.isCancel() || req.isUnique()){
                if (key != null && !key.isEmpty() && req.isCancel()) {
                    pstmtDelete = con.prepareStatement(delKey);
                    pstmtDelete.setString(1, user.toBareJID());
                    pstmtDelete.setString(2, user.getResource());
                    pstmtDelete.setString(3, key);
                    pstmtDelete.executeUpdate();
                } else {
                    pstmtDelete = con.prepareStatement(delActUsr);
                    pstmtDelete.setString(1, user.toBareJID());
                    pstmtDelete.setString(2, user.getResource());
                    pstmtDelete.setString(3, req.getAction());
                    pstmtDelete.setString(4, req.getToUser().toBareJID());
                    pstmtDelete.executeUpdate();
                }
            }

            // In case of cancellation, do not insert a new record.
            if (!req.isCancel()){
                pstmt = con.prepareStatement(q);
                pstmt.setString(1, key != null && !key.isEmpty() ? key : null);
                pstmt.setString(2, req.getAction());
                pstmt.setTimestamp(3, new Timestamp(parent.getTstamp()));
                pstmt.setTimestamp(4, req.getExpiration() == null ? null : new Timestamp(parent.getTstamp() + req.getExpiration()));
                pstmt.setString(5, req.getToUser().toBareJID());
                pstmt.setString(6, req.getToUser().getResource());
                pstmt.setString(7, user.toBareJID());
                pstmt.setString(8, user.getResource());
                pstmt.setInt(9, req.getMessageType());
                pstmt.setBoolean(10, true);
                pstmt.setBoolean(11, req.isUnique());
                pstmt.setBoolean(12, req.requiresAck());
                pstmt.setInt(13, req.getPriority());

                if (req.getAlertKey()==null) {
                    pstmt.setNull(14, Types.INTEGER);
                } else {
                    pstmt.setLong(14, req.getAlertKey());
                }

                pstmt.setString(15, null);
                pstmt.setString(16, null);
                pstmt.setString(17, null);
                pstmt.executeUpdate();
            }

            con.commit();
            return true;
        }
        catch (SQLException e) {
            log.info(e.getMessage(), e);
            rollback(con);
            return false;
        }
        finally {
            if (pstmtDelete != null) {
                DbConnectionManager.closeStatement(pstmtDelete);
            }

            DbConnectionManager.closeConnection(null, pstmt, con);
        }
    }

    /**
     * Processes ACK wrapper and deletes push requests for the ACKed messages.
     * Messages identified by the key are deleted by the query: toUser=ME and msgKey=KEY
     * Messages addressed by timestamp are deleted by the query: toUser=ME and msgAction=ACTION and timestamp<TIMESTAMP.
     * @param ackWrapper
     * @return number of affected message requests (deleted messages).
     */
    public static int persistPushAck(PushAck ackWrapper, boolean expire){
        // Process ACK list and extract deletion criteria.
        final List<PushAckMessage> msgs = ackWrapper.getMessages();
        ArrayList<String> keys2del = new ArrayList<String>(msgs.size());
        ArrayList<ActionTimePair> time2del = new ArrayList<ActionTimePair>(msgs.size());
        for(PushAckMessage msg : msgs){
            final String key = msg.getKey();
            if (key != null && !key.isEmpty()){
                keys2del.add(key);
            }

            // Message can be identified also by time. For multi messages it is handy to delete all older message than
            // the most recent one delivered as the top-message in the push notification.
            final Long timestamp = msg.getTimestamp();
            final String action = msg.getAction();
            if (timestamp == null || timestamp < 100 || action == null || action.isEmpty()){
                continue;
            }

            ActionTimePair pair = new ActionTimePair();
            pair.action = action;
            pair.timestamp = timestamp;
            time2del.add(pair);
        }

        if (keys2del.isEmpty() && time2del.isEmpty()){
            return 0;
        }

        // Delete entries by given criteria.
        int affected = 0;

        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();

            // Expire for given user if set.
            if (expire){
                affected += expirePushRequests(ackWrapper.getFromUser().toBareJID(), con);
            }

            // Delete by message key.
            affected += deletePushRequestByKey(ackWrapper.getFromUser(), keys2del, con);

            // Delete by action time pairs.
            affected += deletePushRequestByActionTimePair(ackWrapper.getFromUser(), time2del, con);
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(con);
        }

        return affected;
    }

    /**
     * Expires db push requests according to its expiration time.
     * @param forUser if null, expires for all users. If not null expires only forUser.
     * @param con DB connection to use for query.
     * @return
     */
    protected static int expirePushRequests(String forUser, Connection con){
        PreparedStatement pstmt = null;
        try {
            if (forUser == null) {
                pstmt = con.prepareStatement(SQL_EXPIRE_PUSH_REQ);
            } else {
                pstmt = con.prepareStatement(SQL_EXPIRE_PUSH_REQ_USR);
                pstmt.setString(1, forUser);
            }

            return pstmt.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }

        return 0;
    }

    /**
     * Deletes push requests from the database according to given message keys destined for given user.
     * @param forUser forUser to delete messages for.
     * @param keys List of message keys that should be deleted.
     * @param con DB connection.
     * @return Number of affected rows.
     */
    protected static int deletePushRequestByKey(JID forUser, List<String> keys, Connection con){
        if (keys == null || keys.isEmpty()){
            return 0;
        }

        PreparedStatement pstmt = null;
        final int cnt = keys.size();
        final String query = String.format(SQL_DELETE_PUSH_REQ_USR_KEY_FMT, genPlaceholders(cnt));
        try {
            pstmt = con.prepareStatement(query);
            pstmt.setString(1, forUser.toBareJID());

            int ctr = 1;
            for (String key : keys) {
                pstmt.setString(++ctr, key);
            }

            return pstmt.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }

        return 0;
    }

    /**
     *
     * @param forUser forUser to delete messages for.
     * @param pairs List of action-time pairs for deletion.
     * @param con DB connection.
     * @return Number of affected rows.
     */
    protected static int deletePushRequestByActionTimePair(JID forUser, List<ActionTimePair> pairs, Connection con){
        if (pairs == null || pairs.isEmpty()){
            return 0;
        }

        PreparedStatement pstmt = null;
        final int cnt = pairs.size();
        try {
            // Build SQL query for action time pairs.
            final String pairTimeFmt = " (ofMsgAction=? AND ofMsgTime<=?) ";
            final StringBuilder sb = new StringBuilder();
            sb.append(" ( ");
            for(int i = 0; i < cnt; i++){
                sb.append(pairTimeFmt);
                if (i+1 < cnt){
                    sb.append(" AND ");
                }
            }
            sb.append(" ) ");

            final String query = String.format(SQL_DELETE_PUSH_REQ_USR_ACTTIME_FMT, sb.toString());
            pstmt = con.prepareStatement(query);
            pstmt.setString(1, forUser.toBareJID());

            int ctr = 1;
            for (ActionTimePair pair : pairs) {
                pstmt.setString(++ctr, pair.action);
                pstmt.setTimestamp(++ctr, new Timestamp(pair.timestamp));
            }

            return pstmt.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }

        return 0;
    }

    /**
     * Deletes push message requests for given user & action, when timestamp older than provided.
     * @param forUser
     * @param pairs
     * @return
     */
    public static int deletePushRequestByActionTimePair(JID forUser, List<ActionTimePair> pairs){
        if (pairs == null || pairs.size() == 0){
            return 0;
        }

        int affected = 0;
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            affected = deletePushRequestByActionTimePair(forUser, pairs, con);
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(con);
        }

        return affected;
    }

    /**
     * Deletes push message requests for given user & action, when timestamp older than provided.
     * @param toUser
     * @param action
     * @param timestamp
     * @return
     */
    public static int deletePushRequestsOlderThan(JID toUser, String action, long timestamp){
        ActionTimePair p = new ActionTimePair();
        p.action = action;
        p.timestamp = timestamp;
        return deletePushRequestByActionTimePair(toUser, Collections.singletonList(p));
    }

    /**
     * Deletes all messages not having wait_ack = 1.
     * @param messages
     * @return
     */
    public static int deleteNoAckWaitPushRequests(Collection<DbPlatformPush> messages){
        if (messages == null || messages.isEmpty()){
            return 0;
        }

        List<Long> idsToDelete = new ArrayList<Long>();
        for(DbPlatformPush msg : messages){
            if (msg.isAckWait()){
                continue;
            }

            idsToDelete.add(msg.getId());
        }

        if (idsToDelete.isEmpty()){
            return 0;
        }


        PreparedStatement pstmt = null;
        final int cnt = idsToDelete.size();
        final String query = String.format(SQL_DELETE_PUSH_REQ_ID_FMT, genPlaceholders(cnt));
        Connection con = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(query);

            int ctr = 0;
            for (Long id : idsToDelete) {
                pstmt.setLong(++ctr, id);
            }

            return pstmt.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeConnection(con);
        }

        return 0;
    }

    /**
     * Keeps topN records for pairs (forUser, action) in the push request message database. Others are deleted.
     * @param topN
     * @return
     */
    public static int cleanDbPushRequestDb(int topN){
        int affected = 0;
        Connection con = null;
        PreparedStatement stCreate = null;
        PreparedStatement stDelete = null;
        try {
            con = DbConnectionManager.getConnection();

            final String tempTableCreate = String.format(SQL_CLEAN_PUSH_CREATE_TEMP_TABLE_FMT, topN);
            stCreate = con.prepareStatement(tempTableCreate);
            stCreate.execute();

            stDelete = con.prepareStatement(SQL_CLEAN_PUSH_DELETE_BY_TEMP_TABLE);
            affected += stDelete.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(con);
            DbConnectionManager.closeStatement(stCreate);
            DbConnectionManager.closeStatement(stDelete);
        }

        return affected;
    }

    /**
     * Keeps topN records for pairs (forUser, action) in the push request message database for particular user and action.
     * Others are deleted.
     *
     * @param topN
     * @return
     */
    public static int cleanDbPushRequestDb(int topN, JID user, String action){
        int affected = 0;
        Connection con = null;
        PreparedStatement stCreate = null;
        PreparedStatement stDelete = null;
        try {
            con = DbConnectionManager.getConnection();

            final String tempTableCreate = String.format(SQL_CLEAN_PUSH_CREATE_TEMP_TABLE_USER_ACTION_FMT, topN);
            stCreate = con.prepareStatement(tempTableCreate);
            stCreate.setString(1, user.toBareJID());
            stCreate.setString(2, action);

            stCreate.execute();

            stDelete = con.prepareStatement(SQL_CLEAN_PUSH_DELETE_BY_TEMP_TABLE);
            affected += stDelete.executeUpdate();
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(con);
            DbConnectionManager.closeStatement(stCreate);
            DbConnectionManager.closeStatement(stDelete);
        }

        return affected;
    }

    /**
     * Action time pair for addressing push request messages in the database. Used for push message request removal.
     */
    public static class ActionTimePair {
        public String action;
        public long timestamp;
    }

    /**
     * Rollback current transaction, catching all exceptions.
     * @param con Connection.
     */
    public static void rollback(Connection con){
        if (con == null){
            return;
        }

        try {
            con.rollback();
        } catch (Exception e1) {
            log.error("Rollback exception", e1);
        }
    }

    /**
     * Generates placeholder list with given number of elements.
     * Example for 4: "?,?,?,?"
     * @param num
     * @return
     */
    public static String genPlaceholders(int num){
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<num; i++){
            sb.append("?");
            if ((i+1) < num){
                sb.append(",");
            }
        }
        return sb.toString();
    }
}
