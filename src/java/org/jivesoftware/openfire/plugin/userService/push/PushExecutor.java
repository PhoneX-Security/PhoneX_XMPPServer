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
    private final ConcurrentLinkedQueue<Job> jobs = new ConcurrentLinkedQueue<Job>();

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
        jobs.add(new Job(name, job));
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public void submit(Job job){
        jobs.add(job);
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

            while(!jobs.isEmpty() && isWorking){
                Job job = jobs.poll();
                if (job == null){
                    continue;
                }

                // Job is waiting for service reference.
                job.setSvc(svc);

                // Run the job.
                if (job.name != null){
                    log.info("<job_"+job.name+">");
                }

                job.run();

                if (job.name != null){
                    log.info("</job_"+job.name+">");
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
    public static class Job implements Runnable {
        private final PushRunnable job;
        private String name;
        private WeakReference<PushService> svc;

        public Job(PushRunnable job) {
            this.job = job;
        }

        public Job(String name, PushRunnable job) {
            this.name = name;
            this.job = job;
        }

        @Override
        public void run() {
            try {
                job.run(svc == null ? null : svc.get());
            } catch(Exception e){
                log.error(String.format("Exception in executing a job %s", name), e);
            }
        }

        public void setSvc(PushService svc) {
            this.svc = new WeakReference<PushService>(svc);
        }
    }
}
