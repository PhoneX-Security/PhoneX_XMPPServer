package org.jivesoftware.openfire.plugin.userService.platformPush.gcm;

import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.platformPush.PlatformPushHandler;
import org.jivesoftware.openfire.plugin.userService.utils.MiscUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;

/**
 * Packet sender worker.
 *
 * Created by dusanklinec on 13.03.15.
 */
public class GcmSender extends Thread {
    private static final Logger log = LoggerFactory.getLogger(GcmSender.class);

    private final WeakReference<UserServicePlugin> pluginRef;
    private final WeakReference<PlatformPushHandler> svcRef;
    private volatile boolean senderWorking = true;

    private Sender gcmSender;
    private String apiKey;
    private Random random;

    /**
     * Initial delay before first retry, without jitter.
     */
    protected static final int BACKOFF_INITIAL_DELAY = 1000;

    /**
     * Maximum delay before a retry.
     */
    protected static final int MAX_BACKOFF_DELAY = 1024000;

    /**
     * Default constructor.
     * @param svc
     */
    public GcmSender(PlatformPushHandler svc, UserServicePlugin plugin) {
        this.svcRef = new WeakReference<PlatformPushHandler>(svc);
        this.pluginRef = new WeakReference<UserServicePlugin>(plugin);
        this.random = new Random();

        // Load API key.
        apiKey = JiveGlobals.getProperty("phonex.gcm.apikey", "");
        if (MiscUtils.isEmpty(apiKey)){
            log.error("API key for GCM is null");
        } else {
            log.info(String.format("API key for GCM loaded, length: %s, prefix: %s",
                    apiKey.length(), MiscUtils.substring(apiKey, 0, 6)));
        }

        gcmSender = new Sender(apiKey);
        this.setName("GcmSender");
    }

    public void deinit(){
        log.info("Deinitializing GCM sender thread");
        senderWorking = false;
    }

    public void run(){
        log.info("GCM Sender thread started.");

        // Main working loop.
        while(senderWorking){
            final PlatformPushHandler svc = svcRef.get();
            if (svc == null){
                senderWorking = false;
                log.info("Manager disappeared");
                break;
            }

            // Iterate send queue and process all records.
            final PriorityBlockingQueue<GcmSendRecord> sndQueue = svc.getGcmQueue();
            while(!sndQueue.isEmpty() && senderWorking){
                GcmSendRecord sndRec = sndQueue.poll();
                if (sndRec == null){
                    continue;
                }

                final long curTime = System.currentTimeMillis();

                // Compare time of sending with current time. If we have some time, take some nap.
                if ((curTime - sndRec.getSendTstamp()) < 0){
                    // Add back to queue and take a short nap.
                    svc.addSendRecord(sndRec, false);
                    break;
                }

                // If sending counter is too high, drop off from the queue.
                if (sndRec.getResendAttempt() > sndRec.getMaxRetries()){
                    log.info(String.format("Send counter too high for packet %s to %s, dropping", sndRec.getPacketId(), sndRec.getTo()));

                    // Store delivery result to database so it is not tried to deliver again.
                    svc.onGcmSendFailed(sndRec);
                    continue;
                }

                // Send
                try {
                    sndRec.incSendCtr();
                    sndRec.setLastSendTstamp(curTime);
                    Result gcmResult = null;

                    // Do GCM send.
                    try {
                        gcmResult = gcmSender.sendNoRetry(sndRec.getPushMsg(), sndRec.getTo());
                    } catch(Exception e){
                        log.error("Exception in sending GCM", e);
                    }

                    log.info(String.format("Packet sent, id: %s, result: %s", sndRec.getPacketId(), gcmResult));
                    if (gcmResult == null){
                        // Re-schedule sending of this packet.
                        // If there is no client session anymore (client offline) this is not reached thus give some
                        // reasonable resend boundary, e.g. 10 attempts.
                        final int backoff = sndRec.getBackoff();
                        final long sleepTime = backoff / 2 + random.nextInt(backoff);
                        if (2 * backoff < MAX_BACKOFF_DELAY) {
                            sndRec.setBackoff(backoff*2);
                        }

                        sndRec.setSendTstamp(System.currentTimeMillis() + sleepTime);
                        svc.addSendRecord(sndRec, true);
                        log.info(String.format("Packet %s re-scheduled with offset %d to %s. ResendAttempt %d",
                                sndRec.getPacketId(), sleepTime, sndRec.getTo(), sndRec.getResendAttempt()));
                    } else {
                        svc.onGcmSendSuccess(sndRec, gcmResult);
                    }

                } catch(Exception ex){
                    log.error("Error during sending a packet", ex);
                }
            }

            try {
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }

        log.info("Sender thread finishing.");
    }
}
