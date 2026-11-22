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

    @Column(nullable = false, length = 20)
    private String strategy;

    @Column(nullable = false, length = 20)
    private String privacyLevel;

    @Column(nullable = false, length = 20)
    private String status;

    private Integer faceCount;
    private Double psnr;
    private Double ssim;
    private Double elapsedMs;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String resultImageBase64;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProcessingTask() {
    }

    public ProcessingTask(String originalFilename, String strategy, String privacyLevel) {
        this.originalFilename = originalFilename;
        this.strategy = strategy;
        this.privacyLevel = privacyLevel;
        this.status = "PROCESSING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void complete(int faceCount, double psnr, double ssim, double elapsedMs, String resultImageBase64) {
        this.status = "COMPLETED";
        this.faceCount = faceCount;
        this.psnr = psnr;
        this.ssim = ssim;
        this.elapsedMs = elapsedMs;
        this.resultImageBase64 = resultImageBase64;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStrategy() { return strategy; }
    public String getPrivacyLevel() { return privacyLevel; }
    public String getStatus() { return status; }
    public Integer getFaceCount() { return faceCount; }
    public Double getPsnr() { return psnr; }
    public Double getSsim() { return ssim; }
    public Double getElapsedMs() { return elapsedMs; }
    public String getResultImageBase64() { return resultImageBase64; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
