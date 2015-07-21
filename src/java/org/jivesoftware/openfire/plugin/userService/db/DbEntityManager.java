package org.jivesoftware.openfire.plugin.userService.db;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.plugin.userService.clientState.ActivityRecord;
import org.jivesoftware.openfire.plugin.userService.platformPush.TokenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by dusanklinec on 12.03.15.
 */
public class DbEntityManager {
    private static final Logger log = LoggerFactory.getLogger(DbEntityManager.class);
    private static final String SQL_FETCH_MESSAGES_USR_ACTION="SELECT * from ofPushMessages WHERE forUser=? AND msgAction=?";
    private static final String SQL_FETCH_MESSAGES_USR="SELECT * from ofPushMessages WHERE forUser=?";
    private static final String SQL_DELETE_MESSAGES_FMT="DELETE from ofPushMessages WHERE msgID IN(%s)";
    private static final String SQL_DELETE_MESSAGES_USR="DELETE from ofPushMessages WHERE msgID msgAction=? AND forUser=?";

    /**
     * Helper method for timestamp extraction from NULL field.
     * @param rs
     * @param colname
     * @return
     * @throws SQLException
     */
    public static Long getTimeStamp(ResultSet rs, String colname) throws SQLException {
        final Date date = rs.getDate(colname);
        return date == null ? null : date.getTime();
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
            msg.setTstamp(rs.getDate(DbPushMessage.FIELD_TIME).getTime());
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
            msg.setTstamp(rs.getDate(DbPushDelivery.FIELD_TIME).getTime());
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
            ar.setLastActiveMilli(rs.getDate(ActivityRecord.FIELD_ACT_TIME).getTime());
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
            pstmt.setString   (1,  msg.getAction());
            pstmt.setTimestamp(2,  new Timestamp(msg.getTstamp()));
            pstmt.setTimestamp(3,  null);
            pstmt.setString   (4,  msg.getToUser());
            pstmt.setString   (5,  msg.getToResource() == null ? "" : msg.getToResource());
            pstmt.setBoolean  (6,  msg.isDurable());
            pstmt.setBoolean  (7,  msg.isUnique());
            pstmt.setString   (8,  msg.getAux1());
            pstmt.setString   (9,  msg.getAux2());
            pstmt.setString  (10,  msg.getAuxData());

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
            pstmt.setString   (4,  msg.getResource() == null ? "" : msg.getResource());
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
            pstmt.setString   (1, user.asBareJID().toString());
            pstmt.setString   (2, user.getResource());
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
     * Stores last activity record to the database.
     * @param ar
     */
    public static void persistLastActivity(ActivityRecord ar){
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmtDelete = null;
        ResultSet rs = null;

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
            pstmt.setString   (2, user.getResource());
            pstmt.setTimestamp(3, new Timestamp(ar.getLastActiveMilli()));
            pstmt.setInt      (4, ar.getLastState());
            pstmt.executeUpdate();
            con.commit();
        }
        catch (SQLException e) {
            log.info(e.getMessage(), e);
            rollback(con);
        }
        finally {
            DbConnectionManager.closeStatement(pstmtDelete);
            DbConnectionManager.closeConnection(rs, pstmt, con);
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