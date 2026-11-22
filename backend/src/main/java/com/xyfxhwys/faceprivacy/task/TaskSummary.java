package com.xyfxhwys.faceprivacy.task;

import java.time.Instant;

public record TaskSummary(
        Long id,
        String originalFilename,
        String ownerUsername,
        String strategy,
        String appliedStrategy,
        String privacyLevel,
        String status,
        String priority,
        int attempt,
        Integer faceCount,
        TaskView.Metrics metrics,
        TaskView.PrivacyEvaluation privacyEvaluation,
        Instant createdAt,
        String errorMessage
) {
    public static TaskSummary from(ProcessingTask task) {
        TaskView view = TaskView.from(task);
        return new TaskSummary(
                view.id(),
                view.originalFilename(),
                view.ownerUsername(),
                view.strategy(),
                view.appliedStrategy(),
                view.privacyLevel(),
                view.status(),
                view.priority(),
                view.attempt(),
                view.faceCount(),
                view.metrics(),
                view.privacyEvaluation(),
                view.createdAt(),
                view.errorMessage()
        );
    }
}
