package com.xyfxhwys.faceprivacy.task;

public class TaskProcessingException extends RuntimeException {
    private final Long taskId;

    public TaskProcessingException(Long taskId, String message, Throwable cause) {
        super(message, cause);
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
