package com.xyfxhwys.faceprivacy.task;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(long id) {
        super("task not found: " + id);
    }
}
