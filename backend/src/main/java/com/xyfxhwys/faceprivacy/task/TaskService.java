package com.xyfxhwys.faceprivacy.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyfxhwys.faceprivacy.service.PythonImageService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TaskService {

    private final ProcessingTaskRepository repository;
    private final PythonImageService pythonImageService;
    private final ObjectMapper objectMapper;

    public TaskService(
            ProcessingTaskRepository repository,
            PythonImageService pythonImageService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.pythonImageService = pythonImageService;
        this.objectMapper = objectMapper;
    }

    public TaskView process(MultipartFile file, String strategy, String privacyLevel) {
        String filename = file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
        ProcessingTask task = repository.save(new ProcessingTask(filename, strategy, privacyLevel, "normal"));
        try {
            task.markProcessing();
            execute(task, file.getBytes());
            return TaskView.from(repository.save(task));
        } catch (Exception ex) {
            String message = userMessage(ex);
            task.fail(message);
            repository.save(task);
            throw new TaskProcessingException(task.getId(), message, ex);
        }
    }

    public ProcessingTask createQueued(String filename, String strategy, String privacyLevel, String priority) {
        return repository.save(new ProcessingTask(filename, strategy, privacyLevel, priority));
    }

    public void execute(ProcessingTask task, byte[] rawImage) {
        try {
            JsonNode result = objectMapper.readTree(
                    pythonImageService.process(rawImage, task.getOriginalFilename(), task.getStrategy(), task.getPrivacyLevel())
            );
            JsonNode metrics = result.path("metrics");
            String appliedStrategy = result.path("strategy").asText(task.getStrategy());
            String agentTrace = result.has("agentTrace")
                    ? objectMapper.writeValueAsString(result.path("agentTrace"))
                    : null;
            JsonNode privacyEvaluation = result.path("privacyEvaluation");
            task.complete(
                    result.path("faceCount").asInt(),
                    metrics.path("psnr").asDouble(),
                    metrics.path("ssim").asDouble(),
                    metrics.path("elapsedMs").asDouble(),
                    result.path("imageBase64").asText(),
                    appliedStrategy,
                    agentTrace,
                    privacyEvaluation.path("detectedFacesAfter").asInt(),
                    privacyEvaluation.path("detectionSuppressionRate").asDouble()
            );
            repository.save(task);
        } catch (Exception ex) {
            throw new TaskRunException(userMessage(ex), ex);
        }
    }

    public List<TaskSummary> recent() {
        return repository.findTop20ByOrderByCreatedAtDesc().stream().map(TaskSummary::from).toList();
    }

    public TaskView find(long id) {
        return repository.findById(id)
                .map(TaskView::from)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public TaskStatistics statistics() {
        List<ProcessingTask> tasks = repository.findAll();
        long completed = tasks.stream().filter(task -> "COMPLETED".equals(task.getStatus())).count();
        long processing = tasks.stream()
                .filter(task -> "QUEUED".equals(task.getStatus()) || "PROCESSING".equals(task.getStatus()))
                .count();
        long failed = tasks.stream().filter(task -> "FAILED".equals(task.getStatus())).count();
        long agentTasks = tasks.stream().filter(task -> "auto".equals(task.getStrategy())).count();
        double averageSsim = average(tasks, true);
        double averagePsnr = average(tasks, false);
        Map<String, Long> strategyCounts = new LinkedHashMap<>();
        tasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()))
                .map(ProcessingTask::getAppliedStrategy)
                .forEach(strategy -> strategyCounts.merge(strategy, 1L, Long::sum));
        return new TaskStatistics(
                tasks.size(),
                completed,
                processing,
                failed,
                agentTasks,
                averageSsim,
                averagePsnr,
                strategyCounts
        );
    }

    private double average(List<ProcessingTask> tasks, boolean ssimMetric) {
        return tasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()))
                .map(task -> ssimMetric ? task.getSsim() : task.getPsnr())
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    public ProcessingTask findEntity(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public ProcessingTask save(ProcessingTask task) {
        return repository.save(task);
    }

    private String userMessage(Exception ex) {
        if (ex instanceof TaskProcessingException taskProcessingException) {
            return taskProcessingException.getMessage();
        }
        if (ex instanceof IOException) {
            return "无法解析图像处理服务返回的数据";
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "图像处理失败，请检查服务状态" : message;
    }
}
