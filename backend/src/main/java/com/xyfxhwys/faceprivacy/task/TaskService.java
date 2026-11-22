package com.xyfxhwys.faceprivacy.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyfxhwys.faceprivacy.service.PythonImageService;
import java.io.IOException;
import java.util.List;

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
        ProcessingTask task = repository.save(new ProcessingTask(filename, strategy, privacyLevel));
        try {
            JsonNode result = objectMapper.readTree(pythonImageService.process(file, strategy, privacyLevel));
            JsonNode metrics = result.path("metrics");
            task.complete(
                    result.path("faceCount").asInt(),
                    metrics.path("psnr").asDouble(),
                    metrics.path("ssim").asDouble(),
                    metrics.path("elapsedMs").asDouble(),
                    result.path("imageBase64").asText()
            );
            return TaskView.from(repository.save(task));
        } catch (Exception ex) {
            String message = userMessage(ex);
            task.fail(message);
            repository.save(task);
            throw new TaskProcessingException(task.getId(), message, ex);
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
