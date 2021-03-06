package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.utils.JobLoggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executor engine.
 * Created by dusanklinec on 13.03.15.
 */
public class TaskExecutor implements Runnable{
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

    // Worker thread
    private Thread wThread;
    private final AtomicInteger threadCnt = new AtomicInteger(0);
    private final AtomicInteger numDeadlockDetections = new AtomicInteger(0);

    /**
     * Default constructor.
     * @param svc
     */
    public TaskExecutor(UserServicePlugin svc) {
        this.plugRef = new WeakReference<UserServicePlugin>(svc);
    }

    /**
     * Starting an executor.
     */
    public void start(){
        startWorkerThread();
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
            jobLogger.setMessagesNumber(250);
            qJob.setLogger(jobLogger);
        }

        // Check queue size.
        final int jobSize = jobs.size();
        log.info(String.format("Adding task to executor [%s], jobQueueSize: %d, curTimestamp: %d",
                qJob.getTaskName(), jobSize, System.currentTimeMillis()));

        // Add the job to the queue
        jobs.add(qJob);

        // Detect long running jobs / deadlocks.
        if (lastJobTimeStart.get() != 0 && lastJobTimeFinish.get() == 0){
            final long curTime = System.currentTimeMillis();
            final long runTime = curTime - lastJobTimeStart.get();

            // Give it a time, might be a long running task
            if (runTime < 1000*10){
                return;
            }

            // Mail notification body;
            final StringBuilder sb = new StringBuilder();

            // Now we are talking about deadlocked thread.
            if (numDeadlockDetections.incrementAndGet() > 2){
                final String deadLockMsg = String.format("TaskExecutor deadlocked by name=%s.%s, runTime: %s, timeStart: %s, now: %s, %s, detections: %s",
                        lastJobName, lastJobID, runTime, lastJobTimeStart.get(), System.currentTimeMillis(), new Date(), numDeadlockDetections.get());

                log.warn(deadLockMsg);
                log.info(deadLockMsg);

            } else {
                final String logInfo = String.format("TaskExecutor: Long running task detected, name=%s.%s, runTime: %s, timeStart: %s, now: %s, %s",
                        lastJobName, lastJobID, runTime, lastJobTimeStart.get(), System.currentTimeMillis(), new Date());

                log.info(logInfo);
                log.warn(logInfo);
                sb.append(logInfo);

                // Job last logs to detect where it got was stuck.
                if (lastJob != null) {
                    final long threadId = Thread.currentThread().getId();
                    final String extraTaskInfo = String.format("Job: %s.%s, #%ss log: {{%s}}",
                            lastJobName, lastJobID, threadId, lastJob.getLogger().dumpMessages());

                    log.info(extraTaskInfo);
                    sb.append("\n\n");
                    sb.append(extraTaskInfo);

                } else {
                    log.info("Last job was null, cannot provide debug info");

                }

                // Thread dump:
                sb.append("\n\nThread dump:\n");
                sb.append(threadDump());
                sb.append("\n\n");

                // Advise user to do inspection on deadlocked system, if possible.
                sb.append("If possible, try to obtain thread dump directly from the JVM, by calling\n");
                sb.append("kill -QUIT <pid>\n");
                sb.append("Or use jstack.");

                // Administrator mail notification.
                final UserServicePlugin plugin = plugRef.get();
                if (plugin != null) {
                    plugin.notifyAdminByMail("OpenFire: System deadlock",
                            "TaskExecutor is experiencing an unpleasant situation - a deadlock." +
                                    "Some task started and refuses to finish.\n\n " +
                                    sb.toString());
                }
            }

//            if (runTime > (1000l * 60l * 5l)){
//                log.info("Trying to kill frozen thread");
//                restartExecutor();
//            }
        }
    }

    /**
     * Starts a new worker thread.
     */
    protected synchronized void startWorkerThread(){
        final String threadName = "TaskExecutor." + threadCnt.getAndIncrement();
        wThread = new Thread(this, threadName);
        wThread.start();

        log.info(String.format("Executor thread started: %s, tid: %s", threadName, wThread.getId()));
    }

    /**
     * Tries to drastically restart the executor.
     */
    protected synchronized void restartExecutor(){
        if (wThread == null){
            log.warn("Working thread is null");
            return;
        }

        // If already has 2 running threads, do nothing - restart did not went well probably.
        if (threadCnt.get() >= 2){
            log.info("Not going to start a new thread, thread counter too big");
            return;
        }

        // Try to kill worker thread.
        try {
            wThread.interrupt();
            wThread.interrupt();
        } catch(Exception e){
            log.error("Exception in interrupting worker thread", e);
        }

        // Give it some time.
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted", e);
        }

        // Spawn a new worker thread to keep executor running.
        startWorkerThread();
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public void submit(Job job){
        jobs.add(job);
    }

    /**
     * Generates thread dump, used in deadlock detection.
     * @return
     */
    protected String threadDump(){
        final StringBuilder sb = new StringBuilder();
        try {
            final Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> threadEntry : map.entrySet()) {
                sb.append("Thread:")
                        .append(threadEntry.getKey().getName())
                        .append(":")
                        .append(threadEntry.getKey().getState())
                        .append("\n");

                for (StackTraceElement element : threadEntry.getValue()) {
                    sb.append("--> ")
                            .append(element)
                            .append("\n");
                }
            }
        } catch (Throwable t){
            log.error("Exception when generating thread dump", t);
        }

        return sb.toString();
    }

    /**
     * Main runnable to be executed by working thread.
     */
    public void run(){
        final long threadId = Thread.currentThread().getId();
        log.info(String.format("Job Executor thread started, name: %s, tid: %s",
                Thread.currentThread().getName(),
                threadId));

        // Main working loop.
        while(isWorking && !Thread.interrupted()){
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
                    log.info("<job_"+jobName+" threadId="+threadId+">");
                }

                try {
                    job.run();
                } catch(Throwable t){
                    log.error("Fatal error in executing a job", t);
                }

                if (jobName != null){
                    log.info("</job_"+jobName+" threadId="+threadId+">");
                }

                lastJobTimeFinish.set(System.currentTimeMillis());
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException ie){
                log.warn("Thread interrupted", ie);
                break;
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }

        log.info("Executor thread finishing.");
    }

}
