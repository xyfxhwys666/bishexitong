package com.xyfxhwys.faceprivacy.task;

import java.time.Instant;

public record TaskView(
        Long id,
        String originalFilename,
        String strategy,
        String appliedStrategy,
        String privacyLevel,
        String status,
        String priority,
        int attempt,
        Integer faceCount,
        Metrics metrics,
        PrivacyEvaluation privacyEvaluation,
        String imageBase64,
        String agentTrace,
        Instant createdAt,
        String errorMessage
) {
    public record Metrics(Double psnr, Double ssim, Double elapsedMs) {}
    public record PrivacyEvaluation(Integer detectedFacesAfter, Double detectionSuppressionRate, boolean proxyMetric) {}

    public static TaskView from(ProcessingTask task) {
        Metrics metrics = task.getPsnr() == null
                ? null
                : new Metrics(task.getPsnr(), task.getSsim(), task.getElapsedMs());
        PrivacyEvaluation privacyEvaluation = task.getDetectedFacesAfter() == null
                ? null
                : new PrivacyEvaluation(task.getDetectedFacesAfter(), task.getDetectionSuppressionRate(), true);
        return new TaskView(
                task.getId(),
                task.getOriginalFilename(),
                task.getStrategy(),
                task.getAppliedStrategy(),
                task.getPrivacyLevel(),
                task.getStatus(),
                task.getPriority(),
                task.getAttempt(),
                task.getFaceCount(),
                metrics,
                privacyEvaluation,
                task.getResultImageBase64(),
                task.getAgentTrace(),
                task.getCreatedAt(),
                task.getErrorMessage()
        );
    }
}
