package org.jivesoftware.openfire.plugin.userService.platformPush;

import com.notnoop.apns.ApnsService;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.userService.db.DbEntityManager;
import org.jivesoftware.openfire.plugin.userService.push.PushSendRecord;
import org.jivesoftware.openfire.plugin.userService.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * APN feedback watcher.
 * Periodically queries APN feedback service for disabled tokens and removes them from the database.
 *
 * Created by dusanklinec on 13.03.15.
 */
public class ApnFeedbackWatcher extends Thread {
    private static final Logger log = LoggerFactory.getLogger(ApnFeedbackWatcher.class);

    private final WeakReference<PlatformPushHandler> svcRef;
    private volatile boolean working = true;

    /**
     * Default constructor.
     * @param svc
     */
    public ApnFeedbackWatcher(PlatformPushHandler svc) {
        this.svcRef = new WeakReference<PlatformPushHandler>(svc);
        this.setName("ApnFeedbackWatcher");
    }

    public void deinit(){
        log.info("Deinitializing ApnFeedbackWatcher thread");
        working = false;
    }

    public void run(){
        log.info("Sender ApnFeedbackWatcher started.");
        long lastTrigger = 0;

        // Main working loop.
        while(working){
            PlatformPushHandler svc = svcRef.get();
            if (svc == null){
                working = false;
                log.info("Manager disappeared");
                break;
            }

            final long curTime = System.currentTimeMillis();
            if (curTime - lastTrigger > 1000*60*5){
                lastTrigger = curTime;
                try {
                    log.debug("<obtain feedback>");
                    getFeedback(svc, svc.getApnSvcProd(), false);
                    getFeedback(svc, svc.getApnSvcDevel(), true);
                    log.debug("</obtain feedback>");

                } catch(Exception e){
                    log.error("Exception in managing feedback", e);
                }

            }

            try {
                Thread.sleep(150);
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }

        log.info("ApnFeedbackWatcher thread finishing.");
    }

    /**
     * Obtains feedback and removes invalid tokens.
     * @param svc
     */
    protected void getFeedback(PlatformPushHandler mgr, ApnsService svc, boolean devel){
        Map<String, Date> inactiveDevices = svc.getInactiveDevices();
        if (inactiveDevices == null || inactiveDevices.isEmpty()){
            return;
        }

        log.info(String.format("APN feedback: Number of inactive devices: %d", inactiveDevices.size()));
        DbEntityManager.deleteTokens(inactiveDevices.keySet());

        // Reflect inactive tokens to the service so it can update its caches and last active records.
        mgr.disabledTokensDetected(inactiveDevices.keySet(), devel);
    }
}
