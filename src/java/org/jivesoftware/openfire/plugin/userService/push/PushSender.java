package org.jivesoftware.openfire.plugin.userService.push;

import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Packet sender worker.
 *
 * Created by dusanklinec on 13.03.15.
 */
public class PushSender extends Thread {
    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final WeakReference<PushService> svcRef;
    private volatile boolean senderWorking = true;

    /**
     * Default constructor.
     * @param svc
     */
    public PushSender(PushService svc) {
        this.svcRef = new WeakReference<PushService>(svc);
        this.setName("PushSender");
    }

    public void deinit(){
        log.info("Deinitializing sender thread");
        senderWorking = false;
    }

    public void run(){
        log.info("Sender thread started.");

        // Main working loop.
        while(senderWorking){
            final IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            PushService svc = svcRef.get();
            if (svc == null){
                senderWorking = false;
                log.info("Manager disappeared");
                break;
            }

            // Iterate send queue and process all records.
            final PriorityBlockingQueue<PushSendRecord> sndQueue = svc.getSndQueue();
            while(!sndQueue.isEmpty() && senderWorking){
                PushSendRecord sndRec = sndQueue.poll();
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
                if (sndRec.getResendAttempt() > 30){
                    log.info(String.format("Send counter too high for packet %s to %s, dropping", sndRec.getPacketId(), sndRec.getDestination()));

                    // Store delivery result to database so it is not tried to deliver again.
                    svc.persistAck(sndRec, PushService.ACK_STATUS_FAIL);
                    continue;
                }

                // Send
                try {
                    // Is there still valid route to given destination?
                    boolean hasClientRoute = svc.getPlugin().getRoutingTable().hasClientRoute(sndRec.getDestination());
                    if (!hasClientRoute){
                        log.info(String.format("Client route disappeared meanwhile. Dropping request for id %s user %s", sndRec.getPacketId(), sndRec.getDestination()));
                        continue;
                    }

                    sndRec.incSendCtr();
                    iqRouter.addIQResultListener(sndRec.getPacketId(), svc, 1000 * 30);
                    iqRouter.route(sndRec.getPacket());

                    log.info(String.format("Routing packet to: %s, packetId=%s", sndRec.getDestination(), sndRec.getPacketId()));
                    svc.getPlugin().getRoutingTable().routePacket(sndRec.getDestination(), sndRec.getPacket(), true);
                    sndRec.setLastSendTstamp(curTime);

                    // Store this record to the waiting map where it waits for ack or for timeout.
                    svc.getAckWait().put(sndRec.getPacketId(), sndRec);
                    log.info(String.format("Packet sent, ackWaitSize: %d", svc.getAckWait().size()));
                } catch(Exception ex){
                    log.error("Error during sending a packet", ex);
                }
            }

            try {
                Thread.sleep(150);
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }

        log.info("Sender thread finishing.");
    }
}
