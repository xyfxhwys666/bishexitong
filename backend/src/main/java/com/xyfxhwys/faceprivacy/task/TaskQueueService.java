package com.xyfxhwys.faceprivacy.task;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TaskQueueService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_BATCH_SIZE = 20;

    private final TaskService taskService;
    private final Map<Long, byte[]> inputImages = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            new QueueThreadFactory()
    );

    public TaskQueueService(TaskService taskService) {
        this.taskService = taskService;
    }

    public TaskSummary submit(
            MultipartFile file,
            String strategy,
            String privacyLevel,
            String priority
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        try {
            byte[] raw = file.getBytes();
            if (raw.length > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("image size must be at most 10 MB");
            }
            String filename = file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
            ProcessingTask task = taskService.createQueued(filename, strategy, privacyLevel, priority);
            inputImages.put(task.getId(), raw);
            enqueue(task);
            return TaskSummary.from(task);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read uploaded file", ex);
        }
    }

    public BatchView submitBatch(
            List<MultipartFile> files,
            String strategy,
            String privacyLevel,
            String priority
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("at least one image is required");
        }
        if (files.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("a batch can contain at most " + MAX_BATCH_SIZE + " images");
        }
        List<TaskSummary> tasks = files.stream()
                .map(file -> submit(file, strategy, privacyLevel, priority))
                .toList();
        return new BatchView(tasks.size(), tasks);
    }

    public TaskSummary retry(long id) {
        ProcessingTask task = taskService.findEntity(id);
        if (!"FAILED".equals(task.getStatus())) {
            throw new IllegalArgumentException("only failed tasks can be retried");
        }
        if (!inputImages.containsKey(id)) {
            throw new IllegalStateException("original image is no longer available for retry");
        }
        task.queueForRetry("waiting for retry");
        taskService.save(task);
        enqueue(task);
        return TaskSummary.from(task);
    }

    private void enqueue(ProcessingTask task) {
        executor.execute(new PrioritizedRunnable(
                priorityValue(task.getPriority()),
                sequence.getAndIncrement(),
                () -> runTask(task.getId())
        ));
    }

    private void runTask(long id) {
        ProcessingTask task = taskService.findEntity(id);
        if (!"QUEUED".equals(task.getStatus())) {
            return;
        }
        byte[] raw = inputImages.get(id);
        if (raw == null) {
            task.fail("original image is no longer available");
            taskService.save(task);
            return;
        }

        task.markProcessing();
        taskService.save(task);
        try {
            taskService.execute(task, raw);
            inputImages.remove(id);
        } catch (TaskRunException ex) {
            if (task.getAttempt() < MAX_ATTEMPTS) {
                task.queueForRetry("第 " + task.getAttempt() + " 次处理失败，系统将自动重试");
                taskService.save(task);
                enqueue(task);
            } else {
                task.fail(ex.getMessage());
                taskService.save(task);
            }
        }
    }

    private int priorityValue(String priority) {
        return switch (priority) {
            case "high" -> 2;
            case "low" -> 0;
            default -> 1;
        };
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        inputImages.clear();
    }

    private static final class QueueThreadFactory implements ThreadFactory {
        private final AtomicLong sequence = new AtomicLong(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "face-privacy-task-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class PrioritizedRunnable implements Runnable, Comparable<PrioritizedRunnable> {
        private final int priority;
        private final long sequence;
        private final Runnable delegate;

        private PrioritizedRunnable(int priority, long sequence, Runnable delegate) {
            this.priority = priority;
            this.sequence = sequence;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PrioritizedRunnable other) {
            int priorityOrder = Integer.compare(other.priority, priority);
            return priorityOrder != 0 ? priorityOrder : Long.compare(sequence, other.sequence);
        }
    }
}
