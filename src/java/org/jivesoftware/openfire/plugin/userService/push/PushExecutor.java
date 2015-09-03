package org.jivesoftware.openfire.plugin.userService.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executor engine.
 * Created by dusanklinec on 13.03.15.
 */
public class PushExecutor extends Thread {
    private static final Logger log = LoggerFactory.getLogger(PushExecutor.class);

    private final WeakReference<PushService> svcRef;
    private volatile boolean isWorking = true;
    private final ConcurrentLinkedQueue<PushJob> pushJobs = new ConcurrentLinkedQueue<PushJob>();

    // Tracking last running job - deadlock detection.
    private String lastJobName;
    private String lastJobID;
    private Long lastJobTimeStart;
    private Long lastJobTimeFinish;

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
            log.info("Adding jobs to a non-working PushExecutor.");
            log.error("Adding jobs to a non-working PushExecutor.");
        }

        // Detect long running jobs.
        if (lastJobTimeStart != null && lastJobTimeFinish == null){
            final long curTime = System.currentTimeMillis();
            final long runTime = curTime - lastJobTimeStart;
            if (runTime > 1000*10){
                final String logInfo = String.format("PushExecutor: Long running task detected, name=%s.%s, runTime: %s, timeStart: %s",
                        lastJobName, lastJobID, runTime, lastJobTimeStart);

                log.info(logInfo);
                log.warn(logInfo);
            }
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

                lastJobName = pushJob.name;
                lastJobID = pushJob.id;
                lastJobTimeStart = System.currentTimeMillis();
                lastJobTimeFinish = null;
                try {
                    // Job is waiting for service reference.
                    pushJob.setSvc(svc);
                    final String jobName = pushJob.name != null ? String.format("%s.%s", pushJob.name, pushJob.id) : null;

                    // Run the job.
                    if (jobName != null) {
                        log.info("<job_" + jobName + ">");
                    }

                    pushJob.run();

                    if (jobName != null) {
                        log.info("</job_" + jobName + ">");
                    }
                } catch(Throwable t){
                    log.error("Fatal error in executing a job", t);
                } finally {
                    lastJobTimeFinish = System.currentTimeMillis();
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
        private static final AtomicInteger ctr = new AtomicInteger(0);

        private final PushRunnable job;
        private String name;
        private WeakReference<PushService> svc;
        private final String id;

        public PushJob(PushRunnable job) {
            this.job = job;
            this.id = String.valueOf(ctr.getAndIncrement());
        }

        public PushJob(String name, PushRunnable job) {
            this.name = name;
            this.job = job;
            this.id = String.valueOf(ctr.getAndIncrement());
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
