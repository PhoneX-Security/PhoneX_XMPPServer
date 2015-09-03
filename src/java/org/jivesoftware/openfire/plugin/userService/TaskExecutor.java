package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Executor engine.
 * Created by dusanklinec on 13.03.15.
 */
public class TaskExecutor extends Thread {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final WeakReference<UserServicePlugin> plugRef;
    private volatile boolean isWorking = true;
    private final ConcurrentLinkedQueue<Job> jobs = new ConcurrentLinkedQueue<Job>();

    // Tracking last running job - deadlock detection.
    private String lastJobName;
    private String lastJobID;
    private Long lastJobTimeStart;
    private Long lastJobTimeFinish;

    /**
     * Default constructor.
     * @param svc
     */
    public TaskExecutor(UserServicePlugin svc) {
        this.plugRef = new WeakReference<UserServicePlugin>(svc);
        this.setName("TaskExecutor");
    }

    /**
     * Shutting down procedure.
     */
    public void deinit(){
        log.info("Deinitializing job executor thread");
        isWorking = false;
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public void submit(String name, JobRunnable job){
        jobs.add(new Job(name, job));

        // Detect long running jobs.
        if (lastJobTimeStart != null && lastJobTimeFinish == null){
            final long curTime = System.currentTimeMillis();
            final long runTime = curTime - lastJobTimeStart;
            if (runTime > 1000*10){
                final String logInfo = String.format("TaskExecutor: Long running task detected, name=%s.%s, runTime: %s, timeStart: %s",
                        lastJobName, lastJobID, runTime, lastJobTimeStart);

                log.info(logInfo);
                log.warn(logInfo);
            }
        }
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public void submit(Job job){
        jobs.add(job);
    }

    public void run(){
        log.info("Job Executor thread started.");


        // Main working loop.
        while(isWorking){
            UserServicePlugin svc = plugRef.get();
            if (svc == null){
                isWorking = false;
                log.info("Manager disappeared");
                break;
            }

            while(!jobs.isEmpty() && isWorking){
                final Job job = jobs.poll();
                if (job == null){
                    continue;
                }

                // Job is waiting for service reference.
                job.setSvc(svc);
                final String jobName = job.getName() != null ? String.format("%s.%s", job.getName(), job.getId()) : null;
                lastJobName = job.getName();
                lastJobID = job.getId();
                lastJobTimeStart = System.currentTimeMillis();
                lastJobTimeFinish = null;

                // Run the job.
                if (jobName != null){
                    log.info("<job_"+jobName+">");
                }

                try {
                    job.run();
                } catch(Throwable t){
                    log.error("Fatal error in executing a job", t);
                }

                if (jobName != null){
                    log.info("</job_"+jobName+">");
                }

                lastJobTimeFinish = System.currentTimeMillis();;
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

}
