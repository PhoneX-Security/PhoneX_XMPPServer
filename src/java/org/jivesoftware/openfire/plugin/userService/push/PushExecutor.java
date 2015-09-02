package org.jivesoftware.openfire.plugin.userService.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Executor engine.
 * Created by dusanklinec on 13.03.15.
 */
public class PushExecutor extends Thread {
    private static final Logger log = LoggerFactory.getLogger(PushExecutor.class);

    private final WeakReference<PushService> svcRef;
    private volatile boolean isWorking = true;
    private final ConcurrentLinkedQueue<PushJob> pushJobs = new ConcurrentLinkedQueue<PushJob>();

    /**
     * Default constructor.
     * @param svc
     */
    public PushExecutor(PushService svc) {
        this.svcRef = new WeakReference<PushService>(svc);
        this.setName("PushExecutor");
    }

    /**
     * Shutting down procedure.
     */
    public void deinit(){
        log.info("Deinitializing executor thread");
        isWorking = false;
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public void submit(String name, PushRunnable job){
        pushJobs.add(new PushJob(name, job));
        if (!isWorking){
            log.error("Adding jobs to a non-working executor.");
        }
    }

    /**
     * Submit a job to the executor.
     * @param pushJob
     */
    public void submit(PushJob pushJob){
        pushJobs.add(pushJob);
    }

    public void run(){
        log.info("Executor thread started.");

        // Main working loop.
        while(isWorking){
            PushService svc = svcRef.get();
            if (svc == null){
                isWorking = false;
                log.info("Manager disappeared");
                break;
            }

            while(!pushJobs.isEmpty() && isWorking){
                final PushJob pushJob = pushJobs.poll();
                if (pushJob == null){
                    continue;
                }

                try {
                    // Job is waiting for service reference.
                    pushJob.setSvc(svc);

                    // Run the job.
                    if (pushJob.name != null) {
                        log.info("<job_" + pushJob.name + ">");
                    }

                    pushJob.run();

                    if (pushJob.name != null) {
                        log.info("</job_" + pushJob.name + ">");
                    }
                } catch(Throwable t){
                    log.error("Fatal error in executing a job", t);
                }
            }

            try {
                Thread.sleep(150);
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }

        log.info("Executor thread finishing.");
    }

    /**
     * Base class for jobs execution.
     */
    public static class PushJob implements Runnable {
        private final PushRunnable job;
        private String name;
        private WeakReference<PushService> svc;

        public PushJob(PushRunnable job) {
            this.job = job;
        }

        public PushJob(String name, PushRunnable job) {
            this.name = name;
            this.job = job;
        }

        @Override
        public void run() {
            try {
                job.run(svc == null ? null : svc.get());
            } catch(Throwable e){
                log.error(String.format("Exception in executing a job %s", name), e);
            }
        }

        public void setSvc(PushService svc) {
            this.svc = new WeakReference<PushService>(svc);
        }
    }
}
