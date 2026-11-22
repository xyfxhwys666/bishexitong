package com.xyfxhwys.faceprivacy.task;

import java.time.Instant;

public record TaskView(
        Long id,
        String originalFilename,
        String strategy,
        String privacyLevel,
        String status,
        Integer faceCount,
        Metrics metrics,
        String imageBase64,
        Instant createdAt,
        String errorMessage
) {
    public record Metrics(Double psnr, Double ssim, Double elapsedMs) {}

    public static TaskView from(ProcessingTask task) {
        Metrics metrics = task.getPsnr() == null
                ? null
                : new Metrics(task.getPsnr(), task.getSsim(), task.getElapsedMs());
        return new TaskView(
                task.getId(),
                task.getOriginalFilename(),
                task.getStrategy(),
                task.getPrivacyLevel(),
                task.getStatus(),
                task.getFaceCount(),
                metrics,
                task.getResultImageBase64(),
                task.getCreatedAt(),
                task.getErrorMessage()
        );
    }
}
