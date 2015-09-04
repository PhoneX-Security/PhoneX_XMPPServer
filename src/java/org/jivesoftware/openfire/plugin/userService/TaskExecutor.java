package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.utils.JobLoggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong lastJobTimeStart = new AtomicLong(0);
    private final AtomicLong lastJobTimeFinish = new AtomicLong(0);
    private volatile Job lastJob;

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
        final Job qJob = new Job(name, job);

        // For bulk roster sync, apply special logging to detect deadlocks.
        if (name != null && name.contains("bulkRosterSync")){
            final JobLoggerImpl jobLogger = new JobLoggerImpl();
            jobLogger.setMessagesNumber(20);
            qJob.setLogger(jobLogger);
        }

        jobs.add(qJob);

        // Detect long running jobs.
        if (lastJobTimeStart.get() != 0 && lastJobTimeFinish.get() == 0){
            final long curTime = System.currentTimeMillis();
            final long runTime = curTime - lastJobTimeStart.get();
            if (runTime > 1000*10){
                final String logInfo = String.format("TaskExecutor: Long running task detected, name=%s.%s, runTime: %s, timeStart: %s",
                        lastJobName, lastJobID, runTime, lastJobTimeStart.get());

                log.info(logInfo);
                log.warn(logInfo);

                // Job last logs to detect where it got was stuck.
                if (lastJob != null) {
                    final long threadId = Thread.currentThread().getId();
                    log.info(String.format("Job: %s.%s, #%ss log: {{%s}}", lastJobName, lastJobID, threadId, lastJob.getLogger().dumpMessages()));
                }
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
                lastJob = job;
                lastJobName = job.getName();
                lastJobID = job.getId();
                lastJobTimeStart.set(System.currentTimeMillis());
                lastJobTimeFinish.set(0);

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

                lastJobTimeFinish.set(System.currentTimeMillis());
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
