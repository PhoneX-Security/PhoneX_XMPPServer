/**
 * $Revision: 1722 $
 * $Date: 2005-07-28 15:19:16 -0700 (Thu, 28 Jul 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin;

import com.google.protobuf.UnknownFieldSet;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.rabbitmq.client.QueueingConsumer;
import net.phonex.pub.proto.PushNotifications;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.plugin.userService.JobRunnable;
import org.jivesoftware.openfire.plugin.userService.TaskExecutor;
import org.jivesoftware.openfire.plugin.userService.amqp.AMQPListener;
import org.jivesoftware.openfire.plugin.userService.amqp.AMQPMsgListener;
import org.jivesoftware.openfire.plugin.userService.clientState.ClientStateService;
import org.jivesoftware.openfire.plugin.userService.geoip.GeoIpHolder;
import org.jivesoftware.openfire.plugin.userService.push.PushService;
import org.jivesoftware.openfire.plugin.userService.roster.TransferRosterItem;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Plugin that allows the administration of users via HTTP requests.
 *
 * @author Justin Hunt
 */
public class UserServicePlugin implements Plugin, PropertyEventListener, AMQPMsgListener {
    private static final Logger log = LoggerFactory.getLogger(UserServicePlugin.class);

    private UserManager userManager;
    private SessionManager sessionManager;
    private RosterManager rosterManager;
    private XMPPServer server;
    private RoutingTable routingTable;
    private PacketDeliverer deliverer;
    private PresenceManager presenceManager;
    private AMQPListener amqpListener;
    private PushService pushSvc;
    private ClientStateService cstateSvc;
    private TaskExecutor executor;

    private String secret;
    private boolean enabled;
    private Collection<String> allowedIPs;

    private Element standardPrivacyListElement;
    private static final String standardPLName = "phonex_roster";

    private static long lastGeoIpRefreshTime = 0;
    private static String geoIpCityDb = "/opt/geoip/GeoLite2-City.mmdb";
    private static DatabaseReader geoipReader;

    // Warning! This privacy list block self publishing since I don't have myself in my 
    // roster, presence update among my devices is blocked...
    private static final String standardPrivacyListString =
            "<list xmlns='jabber:iq:privacy' name='" + standardPLName + "'>"
                    + "     <item type='subscription' value='none' action='deny' order='1'><message/><presence-in/><presence-out/></item>"
                    + "</list>";
    
           /* + "     <item type='subscription' value='both' action='allow' order='1'></item>"
            + "     <item type='subscription' value='from' action='allow' order='2'></item>"
            + "     <item type='subscription' value='to' action='allow' order='3'><message/><presence-in/></item>"
            + "     <item action='deny' order='5'></item>"*/

    public UserServicePlugin() {

    }

    @Override
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        sessionManager = server.getSessionManager();
        rosterManager = server.getRosterManager();
        presenceManager = server.getPresenceManager();
        routingTable = server.getRoutingTable();
        deliverer = server.getPacketDeliverer();
        pushSvc = new PushService(this);
        cstateSvc = new ClientStateService(this);
        executor = new TaskExecutor(this);
        executor.start();

        secret = JiveGlobals.getProperty("plugin.userservice.secret", "");
        // If no secret key has been assigned to the user service yet, assign a random one.
        if (secret.equals("")) {
            secret = StringUtils.randomString(8);
            setSecret(secret);
        }

        // See if the service is enabled or not.
        enabled = JiveGlobals.getBooleanProperty("plugin.userservice.enabled", false);

        // Get the list of IP addresses that can use this service. An empty list means that this filter is disabled.
        allowedIPs = StringUtils.stringToCollection(JiveGlobals.getProperty("plugin.userservice.allowedIPs", ""));

        // Listen to system property events
        PropertyEventDispatcher.addListener(this);

        // Register this as an IQ handler
        pushSvc.init();
        cstateSvc.init();

        // Start AMQP listener
        amqpListener = new AMQPListener();
        amqpListener.init();
        amqpListener.setListener(this);

        // Prepare PrivacyList as an element for privacylist construction further in code.
        try {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            standardPrivacyListElement = xmlReader.read(new StringReader(standardPrivacyListString)).getRootElement();
            log.info("PrivacyList constructed");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void destroyPlugin() {
        userManager = null;
        amqpListener.deinit();
        pushSvc.deinit();
        cstateSvc.deinit();
        executor.deinit();

        // Stop listening to system property events
        PropertyEventDispatcher.removeListener(this);
    }

    public void createUser(String username, String password, String name, String email, String groupNames)
            throws UserAlreadyExistsException, GroupAlreadyExistsException, UserNotFoundException, GroupNotFoundException {
        userManager.createUser(username, password, name, email);
        userManager.getUser(username);

        if (groupNames != null) {
            Collection<Group> groups = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");

            while (tkn.hasMoreTokens()) {
                String groupName = tkn.nextToken();
                Group group = null;

                try {
                    GroupManager.getInstance().getGroup(groupName);
                } catch (GroupNotFoundException e) {
                    // Create this group                    ;
                    GroupManager.getInstance().createGroup(groupName);
                }
                group = GroupManager.getInstance().getGroup(groupName);
                group.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
                group.getProperties().put("sharedRoster.displayName", groupName);
                group.getProperties().put("sharedRoster.groupList", "");

                groups.add(group);
            }
            for (Group group : groups) {
                group.getMembers().add(server.createJID(username, null));
            }
        }
    }

    public void deleteUser(String username) throws UserNotFoundException, SharedGroupException {
        User user = getUser(username);
        userManager.deleteUser(user);

        rosterManager.deleteRoster(server.createJID(username, null));
    }

    /**
     * Lock Out on a given username
     *
     * @param username the username of the local user to disable.
     * @throws UserNotFoundException if the requested user
     *                               does not exist in the local server.
     */
    public void disableUser(String username) throws UserNotFoundException {
        User user = getUser(username);
        LockOutManager.getInstance().disableAccount(username, null, null);
    }

    /**
     * Remove the lockout on a given username
     *
     * @param username the username of the local user to enable.
     * @throws UserNotFoundException if the requested user
     *                               does not exist in the local server.
     */
    public void enableUser(String username) throws UserNotFoundException {
        User user = getUser(username);
        LockOutManager.getInstance().enableAccount(username);
    }

    public void updateUser(String username, String password, String name, String email, String groupNames)
            throws UserNotFoundException, GroupAlreadyExistsException {
        User user = getUser(username);
        if (password != null) user.setPassword(password);
        if (name != null) user.setName(name);
        if (email != null) user.setEmail(email);

        if (groupNames != null) {
            Collection<Group> newGroups = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");

            while (tkn.hasMoreTokens()) {
                String groupName = tkn.nextToken();
                Group group = null;

                try {
                    group = GroupManager.getInstance().getGroup(groupName);
                } catch (GroupNotFoundException e) {
                    // Create this group                    ;
                    group = GroupManager.getInstance().createGroup(groupName);
                    group.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
                    group.getProperties().put("sharedRoster.displayName", groupName);
                    group.getProperties().put("sharedRoster.groupList", "");
                }

                newGroups.add(group);
            }

            Collection<Group> existingGroups = GroupManager.getInstance().getGroups(user);
            // Get the list of groups to add to the user
            Collection<Group> groupsToAdd = new ArrayList<Group>(newGroups);
            groupsToAdd.removeAll(existingGroups);
            // Get the list of groups to remove from the user
            Collection<Group> groupsToDelete = new ArrayList<Group>(existingGroups);
            groupsToDelete.removeAll(newGroups);

            // Add the user to the new groups
            for (Group group : groupsToAdd) {
                group.getMembers().add(server.createJID(username, null));
            }
            // Remove the user from the old groups
            for (Group group : groupsToDelete) {
                group.getMembers().remove(server.createJID(username, null));
            }
        }
    }

    /**
     * Sync the roster with one specified in this request in background thread.
     *
     * @param username
     * @param rosterList
     */
    public void syncRosterInExecutor(final String username, final List<TransferRosterItem> rosterList){
        submit("rosterSync_" + username, new JobRunnable() {
            @Override
            public void run(UserServicePlugin plugin) {
                try {
                    syncRoster(username, rosterList);
                } catch (Exception e) {
                    log.error("Could not sync roster - exception", e);
                }
            }
        });
    }

    /**
     * Sync the roster with one specified in this request.
     *
     * @param username
     * @param rosterList
     * @throws org.jivesoftware.openfire.user.UserNotFoundException
     * @throws org.jivesoftware.openfire.user.UserAlreadyExistsException
     * @throws org.jivesoftware.openfire.SharedGroupException
     */
    public void syncRoster(String username, List<TransferRosterItem> rosterList)
            throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException {

        final User usr = getUser(username);
        final JID prober = server.createJID(username, null);
        Roster r = rosterManager.getRoster(username);

        Set<String> jidInRosterList = new HashSet<String>();
        for (TransferRosterItem tri : rosterList) {
            JID j = new JID(tri.jid);
            jidInRosterList.add(j.toBareJID().toString());
        }

        Set<JID> newRosterEntries = new HashSet<JID>();

        // Update roster according to sync data.
        for (TransferRosterItem tri : rosterList) {
            JID j = new JID(tri.jid);
            RosterItem ri = null;
            try {
                ri = r.getRosterItem(j);
            } catch (UserNotFoundException e) {
                //Roster item does not exist. Try to add it.
            }

            List<String> groups = new ArrayList<String>();
            if (tri.groups != null) {
                StringTokenizer tkn = new StringTokenizer(tri.groups, ",");
                while (tkn.hasMoreTokens()) {
                    groups.add(tkn.nextToken());
                }
            }

            // Tolerance to particular problems in the contact list.
            try {
                // If null, roster item does not exist, thus create it.
                if (ri == null) {
                    ri = r.createRosterItem(j, tri.name, groups, false, true);
                    newRosterEntries.add(j);
                } else {
                    // Already exists, updating.
                    ri.setGroups(groups);
                    ri.setNickname(tri.name);
                }

                // If sub status differs, add to set.
                RosterItem.SubType subType = RosterItem.SubType.getTypeFromInt(tri.subscription);
                if (subType.equals(ri.getSubStatus()) == false) {
                    newRosterEntries.add(j);
                }

                // In both cases.
                ri.setSubStatus(subType);
                if (tri.askStatus != null) {
                    ri.setAskStatus(RosterItem.AskType.getTypeFromInt(tri.askStatus));
                }
                if (tri.recvStatus != null) {
                    ri.setRecvStatus(RosterItem.RecvType.getTypeFromInt(tri.recvStatus));
                }

                r.updateRosterItem(ri);
                log.info(String.format("updating roster for u=%s, dest=%s, sub=%s",
                        username, tri.name, tri.subscription));

                // Protect destination user.
                // TODO: optimize this. It sufficess to create the list when user
                // is being created.
                try {
                    if (server.isLocal(j)) {
                        createDefaultPrivacyList(j.getNode());
                    }
                } catch (Exception ex) {
                    log.error("Problem with creating a default privacy list", ex);
                }
            } catch (Exception e) {
                // Be tolerant to 1 user failing
                log.warn("Problem with adding a user to the roster", e);
            }
        }

        // Delete non-existent roster items from the roster.
        Collection<RosterItem> rosterItems = r.getRosterItems();
        for (RosterItem ri : rosterItems) {
            JID j = ri.getJid();
            String jid = j.asBareJID().toString();
            if (jidInRosterList.contains(jid) == false) {
                unsubscribeRosterItem(prober, r, j);
            }
        }

        // Protect current user.
        createDefaultPrivacyList(username);

        // Update new roster entries added -probe presence and broadcast data.
        log.info(String.format("sync, new roster entries, size=%s, prober=%s", newRosterEntries.size(), username));
        if (!newRosterEntries.isEmpty()) {
            refreshPresenceInfo(prober, newRosterEntries);
        }
    }

    /**
     * Unsubscribes from remote presence.
     *
     * @param master
     * @param r
     * @param item2remove
     */
    private void unsubscribeRosterItem(JID master, Roster r, JID item2remove) {
        try {
            final String itemStr = item2remove.getNode();
            final boolean isLocal = server.isLocal(item2remove);

            RosterItem itemToRemove = r.getRosterItem(item2remove);
            if (itemToRemove != null && !itemToRemove.getSharedGroups().isEmpty()) {
                throw new SharedGroupException("Cannot remove contact that belongs to a shared group");
            }

            if (itemToRemove == null) {
                log.warn("item to remove is null: " + item2remove.getNode());
                return;
            }

            // Router presence state (unavailable) to the remote user.
            Presence presence = new Presence();
            presence.setFrom(master);
            presence.setTo(itemToRemove.getJid());
            presence.setType(Presence.Type.unavailable);
            server.getPacketRouter().route(presence);

            // Change roster record.
            RosterItem.SubType subType = itemToRemove.getSubStatus();
            itemToRemove.setSubStatus(RosterItem.SUB_NONE);
            r.updateRosterItem(itemToRemove);

            // Cancel any existing presence subscription between the user and the contact
            /*if (subType == RosterItem.SUB_TO || subType == RosterItem.SUB_BOTH) {
                Presence presence = new Presence();
                presence.setFrom(server.createJID(master.getNode(), null));
                presence.setTo(item2remove);
                presence.setType(Presence.Type.unsubscribe);
                server.getPacketRouter().route(presence);
            }*/

            // cancel any existing presence subscription between the contact and the user
            /*if (subType == RosterItem.SUB_FROM || subType == RosterItem.SUB_BOTH) {
                Presence presence = new Presence();
                presence.setFrom(server.createJID(master.getNode(), null));
                presence.setTo(itemToRemove.getJid());
                presence.setType(Presence.Type.unsubscribed);
                server.getPacketRouter().route(presence);
            }*/

        } catch (Exception ex) {
            log.warn("Problem with removing roster entry", ex);
        }
    }

    /**
     * Deletes item2remove from the master's r roster.
     * Preserves item2remove's roster state.
     *
     * @param master
     * @param r
     * @param item2remove
     */
    private void deleteRosterItem(JID master, Roster r, JID item2remove) {
        // Delete from the roster if not present in the sync list.
        // The following code fragment is a bit problematic since deleteRosterItem
        // deletes also r from the j's roster (or sets status to NONE).
        // This is not desired since if the user is re-added on the 
        // username side, the j side still has NONE subscription even 
        // though j has username in its contact list.
        try {
            final String itemStr = item2remove.getNode();
            final boolean isLocal = server.isLocal(item2remove);

            // At first try to backup item's roster state against master,
            // in order to be recoverede later.
            RosterItem origItem = null;
            Roster recipientRoster = null;
            if (isLocal) {
                try {
                    recipientRoster = userManager.getUser(itemStr).getRoster();
                    origItem = recipientRoster.getRosterItem(master);
                } catch (UserNotFoundException e) {
                    // Do nothing
                    log.info("User not found: " + item2remove.getNode());
                }
            }

            log.info(String.format("deleting roster for u=%s, dest=%s, local=%s, roster=%s, item=%s, subs=%s",
                    master, item2remove, isLocal, recipientRoster, origItem, origItem == null ? "-1" : origItem.getSubStatus()));

            // Delete item from the roster.
            // This call does a lot of things. It removes subscriptions, sends
            // unsubscribe, unsubscribed messages and many others.
            // It also modifies item's roster.
            r.deleteRosterItem(item2remove, true);

            // Recover item's roster item for master, it there is any.
            // Warning: This method does not work! Openfire processes some parts
            // of the delete request asynchronically thus this code is executed 
            // before update to NONE happens on the item's side.
            if (isLocal && origItem != null && recipientRoster != null) {
                try {
                    RosterItem updatedItem = recipientRoster.getRosterItem(master);
                    if (updatedItem != null) {
                        updatedItem.setAskStatus(origItem.getAskStatus());
                        updatedItem.setRecvStatus(origItem.getRecvStatus());
                        updatedItem.setSubStatus(origItem.getSubStatus());
                        recipientRoster.updateRosterItem(updatedItem);
                    } else {
                        log.info("Roster item was deleted, for " + item2remove.getNode());
                    }


                } catch (Exception ex) {
                    log.warn("Exception in recovering item's roster.", ex);
                }
            }
        } catch (Exception ex) {
            log.warn("Problem with removing roster entry", ex);
        }
    }

    /**
     * Probe each other presence states for the master and all newly added roster
     * entries. Goal: update actual presence data ASAP.
     *
     * @param master
     * @param newRosterEntries
     */
    public void refreshPresenceInfo(JID master, Collection<JID> newRosterEntries) {
        try {
            for (JID probee : newRosterEntries) {
                final String probeeStr = probee.getNode();
                final boolean isLocal = server.isLocal(probee);

                log.info(String.format("probee: %s probeeStr: %s, prober: %s proberStr %s local %s",
                        probee, probeeStr, master, master.toBareJID(), isLocal));

                // Update for user owning the contact list.
                presenceManager.probePresence(master, probee);

                // Update for the remote user.
                presenceManager.probePresence(probee, master);
            }
        } catch (Exception ex) {
            log.warn("Exception in probing presence state", ex);
        }
    }

    /**
     * Non-exception wrapper for canProbePresence call.
     *
     * @param prober
     * @param probee
     * @return
     */
    private boolean canProbePresence(JID prober, String probee) {
        boolean canProbe = false;
        try {
            canProbe = presenceManager.canProbePresence(prober, probee);
        } catch (Exception ex) {

        }

        return canProbe;
    }

    /**
     * Measure to ignore all not from/to local users.
     *
     * @param username
     */
    private void createDefaultPrivacyList(String username) {
        PrivacyListManager privListManager = PrivacyListManager.getInstance();
        PrivacyList list = privListManager.getDefaultPrivacyList(username);
        if (list != null && standardPLName.equals(list.getName())) {
            return;
        }

        // Create a new privacy list for the caller, store to the database
        // and updates a cache.
        synchronized (username.intern()) {
            try {
                PrivacyList nlist = privListManager.createPrivacyList(username, standardPLName, standardPrivacyListElement);
                nlist.setDefaultList(true);

                // Update as master.
                privListManager.changeDefaultList(username, nlist, list);
                log.info("Generated privacy list for: " + username);
            } catch (Exception e) {
                log.warn("Exception in setting a privacy list", e);
            }
        }
    }

    /**
     * Fetches roster for the user.
     *
     * @param username
     * @return
     * @throws org.jivesoftware.openfire.user.UserNotFoundException
     * @throws org.jivesoftware.openfire.user.UserAlreadyExistsException
     * @throws org.jivesoftware.openfire.SharedGroupException
     */
    public List<TransferRosterItem> fetchRoster(String username)
            throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        List<TransferRosterItem> tRosterItems = new LinkedList<TransferRosterItem>();

        // Delete non-existent roster items from the roster.
        Collection<RosterItem> rosterItems = r.getRosterItems();
        for (RosterItem ri : rosterItems) {
            JID j = ri.getJid();
            String jid = j.asBareJID().toString();

            TransferRosterItem tri = new TransferRosterItem();
            tri.jid = jid;
            tri.name = ri.getNickname();
            tri.askStatus = ri.getAskStatus().getValue();
            tri.recvStatus = ri.getRecvStatus().getValue();
            tri.subscription = ri.getSubStatus().getValue();

            List<String> groups = ri.getGroups();
            if (groups == null || groups.isEmpty()) {
                tri.groups = "";
            } else {
                tri.groups = StringUtils.collectionToString(groups);
            }

            tRosterItems.add(tri);
        }

        return tRosterItems;
    }

    /**
     * Fetches all users in the system.
     *
     * @return
     */
    public List<String> fetchUsers() {
        return new LinkedList<String>(userManager.getUsernames());
    }

    /**
     * Add new roster item for specified user
     *
     * @param username     the username of the local user to add roster item to.
     * @param itemJID      the JID of the roster item to be added.
     * @param itemName     the nickname of the roster item.
     * @param subscription the type of subscription of the roster item. Possible values are: -1(remove), 0(none), 1(to), 2(from), 3(both).
     * @param groupNames   the name of a group to place contact into.
     * @throws UserNotFoundException      if the user does not exist in the local server.
     * @throws UserAlreadyExistsException if roster item with the same JID already exists.
     * @throws SharedGroupException       if roster item cannot be added to a shared group.
     */
    public void addRosterItem(String username, String itemJID, String itemName, String subscription, String groupNames)
            throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        try {
            r.getRosterItem(j);
            throw new UserAlreadyExistsException(j.toBareJID());
        } catch (UserNotFoundException e) {
            //Roster item does not exist. Try to add it.
        }

        if (r != null) {
            List<String> groups = new ArrayList<String>();
            if (groupNames != null) {
                StringTokenizer tkn = new StringTokenizer(groupNames, ",");
                while (tkn.hasMoreTokens()) {
                    groups.add(tkn.nextToken());
                }
            }
            RosterItem ri = r.createRosterItem(j, itemName, groups, false, true);
            if (subscription == null) {
                subscription = "0";
            }
            ri.setSubStatus(RosterItem.SubType.getTypeFromInt(Integer.parseInt(subscription)));
            r.updateRosterItem(ri);
        }
    }

    /**
     * Update roster item for specified user
     *
     * @param username     the username of the local user to update roster item for.
     * @param itemJID      the JID of the roster item to be updated.
     * @param itemName     the nickname of the roster item.
     * @param subscription the type of subscription of the roster item. Possible values are: -1(remove), 0(none), 1(to), 2(from), 3(both).
     * @param groupNames   the name of a group.
     * @throws UserNotFoundException if the user does not exist in the local server or roster item does not exist.
     * @throws SharedGroupException  if roster item cannot be added to a shared group.
     */
    public void updateRosterItem(String username, String itemJID, String itemName, String subscription, String groupNames)
            throws UserNotFoundException, SharedGroupException {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        RosterItem ri = r.getRosterItem(j);

        List<String> groups = new ArrayList<String>();
        if (groupNames != null) {
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");
            while (tkn.hasMoreTokens()) {
                groups.add(tkn.nextToken());
            }
        }

        ri.setGroups(groups);
        ri.setNickname(itemName);

        if (subscription == null) {
            subscription = "0";
        }
        ri.setSubStatus(RosterItem.SubType.getTypeFromInt(Integer.parseInt(subscription)));
        r.updateRosterItem(ri);
    }

    /**
     * Delete roster item for specified user. No error returns if nothing to delete.
     *
     * @param username the username of the local user to add roster item to.
     * @param itemJID  the JID of the roster item to be deleted.
     * @throws UserNotFoundException if the user does not exist in the local server.
     * @throws SharedGroupException  if roster item cannot be deleted from a shared group.
     */
    public void deleteRosterItem(String username, String itemJID)
            throws UserNotFoundException, SharedGroupException {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        // No roster item is found. Uncomment the following line to throw UserNotFoundException.
        //r.getRosterItem(j);

        r.deleteRosterItem(j, true);
    }

    /**
     * Returns the the requested user or <tt>null</tt> if there are any
     * problems that don't throw an error.
     *
     * @param username the username of the local user to retrieve.
     * @return the requested user.
     * @throws UserNotFoundException if the requested user
     *                               does not exist in the local server.
     */
    private User getUser(String username) throws UserNotFoundException {
        JID targetJID = server.createJID(username, null);
        // Check that the sender is not requesting information of a remote server entity
        if (targetJID.getNode() == null) {
            // Sender is requesting presence information of an anonymous user
            throw new UserNotFoundException("Username is null");
        }
        return userManager.getUser(targetJID.getNode());
    }

    /**
     * Returns the secret key that only valid requests should know.
     *
     * @return the secret key.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the secret key that grants permission to use the userservice.
     *
     * @param secret the secret key.
     */
    public void setSecret(String secret) {
        JiveGlobals.setProperty("plugin.userservice.secret", secret);
        this.secret = secret;
    }

    public Collection<String> getAllowedIPs() {
        return allowedIPs;
    }

    public void setAllowedIPs(Collection<String> allowedIPs) {
        JiveGlobals.setProperty("plugin.userservice.allowedIPs", StringUtils.collectionToString(allowedIPs));
        this.allowedIPs = allowedIPs;
    }

    /**
     * Returns true if the user service is enabled. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @return true if the user service is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the user service. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @param enabled true if the user service should be enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("plugin.userservice.enabled", enabled ? "true" : "false");
    }

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.userservice.secret")) {
            this.secret = (String) params.get("value");
        } else if (property.equals("plugin.userservice.enabled")) {
            this.enabled = Boolean.parseBoolean((String) params.get("value"));
        } else if (property.equals("plugin.userservice.allowedIPs")) {
            this.allowedIPs = StringUtils.stringToCollection((String) params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.userservice.secret")) {
            this.secret = "";
        } else if (property.equals("plugin.userservice.enabled")) {
            this.enabled = false;
        } else if (property.equals("plugin.userservice.allowedIPs")) {
            this.allowedIPs = Collections.emptyList();
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }

    public static String getSessionId(Session sess) {
        Random rnd = new Random();
        String idRes = Integer.toString(rnd.nextInt(8192));

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("sessionName: ").append(sess.getAddress().toString());

            StreamID streamID = sess.getStreamID();
            if (streamID != null) {
                sb.append(":stream:").append(streamID.getID());
            }

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            idRes = DatatypeConverter.printBase64Binary(md5.digest(sb.toString().getBytes("UTF-8")));
            idRes = idRes.replaceAll("[^a-zA-Z0-9]", "");
            idRes = URLEncoder.encode(idRes.substring(0, 12), "UTF-8");
        } catch (Exception e) {

        }

        return idRes.trim().toLowerCase();
    }

    public static PushNotifications.PresencePush readPresencePush(String base64encodedState) {
        PushNotifications.PresencePush pp = null;
        if (base64encodedState == null || base64encodedState.isEmpty()) {
            return null;
        }

        try {
            // Presence push notification for this contact.
            // Using Google Protocol Buffers to serialize complex structures
            // into presence status text information.    
            final byte[] bpush = DatatypeConverter.parseBase64Binary(base64encodedState);
            pp = PushNotifications.PresencePush.parseFrom(bpush);
        } catch (Exception e) {
            pp = null;
        }

        return pp;
    }

    public static String formatPushPresence(PushNotifications.PresencePush pp) {
        String unpackedPresence = null;
        try {
            // Presence push notification for this contact.
            // Using Google Protocol Buffers to serialize complex structures
            // into presence status text information.
            StringBuilder sb = new StringBuilder();

            // Status.
            if (pp.hasStatus()) {
                sb.append("status: ").append(pp.getStatus().toString()).append("\n");
            }

            // Status text
            if (pp.hasStatusText()) {
                sb.append("statusText: ").append(pp.getStatusText()).append("\n");
            }

            // SIP registered?
            if (pp.hasSipRegistered()) {
                sb.append("sipRegistered: ").append(pp.getSipRegistered()).append("\n");
            }

            // Cert created
            if (pp.hasCertNotBefore()) {
                long time = pp.getCertNotBefore();
                SimpleDateFormat dformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.getDefault());
                String fmtedDate = dformat.format(new Date(time));
                sb.append("certNotBefore: ").append(fmtedDate).append("\ncertUtcMilli:").append(time).append("\n");
            }

            // Cert hash
            if (pp.hasCertHashShort()) {
                sb.append("certHashShort: ").append(pp.getCertHashShort()).append("\n");
            }

            // Cert hash full
            if (pp.hasCertHashFull()) {
                sb.append("certHashFull: ").append(pp.getCertHashFull()).append("\n");
            }

            // Cap skip
            if (pp.hasCapabilitiesSkip()) {
                sb.append("capSkip: ").append(pp.getCapabilitiesSkip()).append("\n");
            }

            // Caps
            final int capsCnt = pp.getCapabilitiesCount();
            if (capsCnt > 0) {
                sb.append("caps: ");
                ArrayList<String> capList = new ArrayList<String>(pp.getCapabilitiesList());
                java.util.Collections.sort(capList);

                for (int i = 0; i < capsCnt; i++) {
                    sb.append(capList.get(i));
                    if (i + 1 != capsCnt) {
                        sb.append("; ");
                    }
                }
                sb.append("\n");
            }

            // Version
            if (pp.hasVersion()) {
                sb.append("version: ").append(pp.getVersion()).append("\n");
            }

            UnknownFieldSet unknownFields = pp.getUnknownFields();
            if (unknownFields != null && unknownFields.isInitialized()) {
                String uFieldsStr = unknownFields.toString();
                if (uFieldsStr != null && !uFieldsStr.isEmpty()) {
                    sb.append("Unknown fields: ").append(unknownFields.toString()).append("\n");
                }
            }

            unpackedPresence = sb.toString();
        } catch (Exception ex) {
            unpackedPresence = null;
        }

        return unpackedPresence;
    }

    /**
     * Receive message from AMQP queue regarding XMPP server, from /phonex/xmpp queue.
     *
     * @param queue
     * @param delivery
     */
    @Override
    public void acceptMessage(String queue, QueueingConsumer.Delivery delivery) {
        try {
            final String message = new String(delivery.getBody());
            log.info("Message received: " + message);

            final JSONObject obj = new JSONObject(message);
            final String action = obj.getString("action");

            // Handle push notification for XMPP destination.
            if ("push".equalsIgnoreCase(action)) {
                pushSvc.handlePushRequestFromQueue(obj);
            } else {
                log.info("Unrecognized action: " + action);
            }

        } catch (Exception ex) {
            log.warn("Exception in processing a new message");
        }
    }

    /**
     * Translates IP address to GeoIP record.
     * @param ip
     * @return
     */
    public static GeoIpHolder getGeoIp(String ip){
        try {
            final long curTime = System.currentTimeMillis();
            if (geoipReader == null || (curTime - lastGeoIpRefreshTime) > 1000*60*10){
                geoipReader = new DatabaseReader.Builder(new File(geoIpCityDb)).build();
                lastGeoIpRefreshTime = curTime;
            }

            final InetAddress addr = InetAddress.getByName(ip);
            final CityResponse city = geoipReader.city(addr);
            if (city != null){
                return GeoIpHolder.build(city);
            }

            return GeoIpHolder.build(geoipReader.country(addr));

        } catch (Exception e) {
            log.error("Exception in geoip translation, make sure " + geoIpCityDb + " exists.", e);
        }

        return null;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public RosterManager getRosterManager() {
        return rosterManager;
    }

    public XMPPServer getServer() {
        return server;
    }

    public RoutingTable getRoutingTable() {
        return routingTable;
    }

    public PacketDeliverer getDeliverer() {
        return deliverer;
    }

    public PresenceManager getPresenceManager() {
        return presenceManager;
    }

    public AMQPListener getAmqpListener() {
        return amqpListener;
    }

    public TaskExecutor getExecutor() {
        return executor;
    }

    public void submit(String name, JobRunnable job) {
        executor.submit(name, job);
    }

    public ClientStateService getCstateSvc() {
        return cstateSvc;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public PushService getPushSvc() {
        return pushSvc;
    }
}
