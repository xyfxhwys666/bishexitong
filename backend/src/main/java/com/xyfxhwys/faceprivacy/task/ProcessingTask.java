package com.xyfxhwys.faceprivacy.task;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "processing_tasks")
public class ProcessingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(length = 64)
    private String ownerUsername;

    @Column(nullable = false, length = 20)
    private String strategy;

    @Column(length = 20)
    private String appliedStrategy;

    @Column(nullable = false, length = 20)
    private String privacyLevel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 20)
    private String priority;

    private Integer attempt;

    private Integer faceCount;
    private Double psnr;
    private Double ssim;
    private Double elapsedMs;

    private Integer detectedFacesAfter;
    private Double detectionSuppressionRate;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String resultImageBase64;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String agentTrace;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProcessingTask() {
    }

    public ProcessingTask(String originalFilename, String strategy, String privacyLevel, String priority) {
        this(originalFilename, strategy, privacyLevel, priority, null);
    }

    public ProcessingTask(
            String originalFilename,
            String strategy,
            String privacyLevel,
            String priority,
            String ownerUsername
    ) {
        this.originalFilename = originalFilename;
        this.ownerUsername = ownerUsername;
        this.strategy = strategy;
        this.appliedStrategy = "auto".equals(strategy) ? null : strategy;
        this.privacyLevel = privacyLevel;
        this.status = "QUEUED";
        this.priority = priority;
        this.attempt = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markProcessing() {
        this.status = "PROCESSING";
        this.attempt = getAttempt() + 1;
        this.updatedAt = Instant.now();
    }

    public void complete(
            int faceCount,
            double psnr,
            double ssim,
            double elapsedMs,
            String resultImageBase64,
            String appliedStrategy,
            String agentTrace,
            int detectedFacesAfter,
            double detectionSuppressionRate
    ) {
        this.status = "COMPLETED";
        this.faceCount = faceCount;
        this.psnr = psnr;
        this.ssim = ssim;
        this.elapsedMs = elapsedMs;
        this.resultImageBase64 = resultImageBase64;
        this.appliedStrategy = appliedStrategy;
        this.agentTrace = agentTrace;
        this.detectedFacesAfter = detectedFacesAfter;
        this.detectionSuppressionRate = detectionSuppressionRate;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void queueForRetry(String errorMessage) {
        this.status = "QUEUED";
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getStrategy() { return strategy; }
    public String getAppliedStrategy() { return appliedStrategy == null ? strategy : appliedStrategy; }
    public String getPrivacyLevel() { return privacyLevel; }
    public String getStatus() { return status; }
    public String getPriority() { return priority == null ? "normal" : priority; }
    public int getAttempt() { return attempt == null ? 0 : attempt; }
    public Integer getFaceCount() { return faceCount; }
    public Double getPsnr() { return psnr; }
    public Double getSsim() { return ssim; }
    public Double getElapsedMs() { return elapsedMs; }
    public Integer getDetectedFacesAfter() { return detectedFacesAfter; }
    public Double getDetectionSuppressionRate() { return detectionSuppressionRate; }
    public String getResultImageBase64() { return resultImageBase64; }
    public String getAgentTrace() { return agentTrace; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
