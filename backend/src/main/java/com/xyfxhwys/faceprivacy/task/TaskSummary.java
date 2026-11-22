package com.xyfxhwys.faceprivacy.task;

import java.time.Instant;

public record TaskSummary(
        Long id,
        String originalFilename,
        String strategy,
        String privacyLevel,
        String status,
        Integer faceCount,
        TaskView.Metrics metrics,
        Instant createdAt,
        String errorMessage
) {
    public static TaskSummary from(ProcessingTask task) {
        TaskView view = TaskView.from(task);
        return new TaskSummary(
                view.id(),
                view.originalFilename(),
                view.strategy(),
                view.privacyLevel(),
                view.status(),
                view.faceCount(),
                view.metrics(),
                view.createdAt(),
                view.errorMessage()
        );
    }
}
