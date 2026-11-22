package com.xyfxhwys.faceprivacy.web;

import com.xyfxhwys.faceprivacy.task.TaskService;
import com.xyfxhwys.faceprivacy.task.TaskSummary;
import com.xyfxhwys.faceprivacy.task.BatchView;
import com.xyfxhwys.faceprivacy.task.TaskQueueService;
import com.xyfxhwys.faceprivacy.task.TaskView;
import com.xyfxhwys.faceprivacy.task.TaskStatistics;
import jakarta.validation.constraints.Pattern;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskQueueService taskQueueService;

    public TaskController(TaskService taskService, TaskQueueService taskQueueService) {
        this.taskService = taskService;
        this.taskQueueService = taskQueueService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskSummary> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "mosaic")
            @Pattern(regexp = "auto|blur|mosaic|cover", message = "strategy must be auto, blur, mosaic or cover") String strategy,
            @RequestParam(name = "privacyLevel", defaultValue = "standard")
            @Pattern(regexp = "light|standard|strict", message = "privacyLevel must be light, standard or strict") String privacyLevel,
            @RequestParam(defaultValue = "normal")
            @Pattern(regexp = "low|normal|high", message = "priority must be low, normal or high") String priority
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(taskQueueService.submit(file, strategy, privacyLevel, priority));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchView> createBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "mosaic")
            @Pattern(regexp = "auto|blur|mosaic|cover", message = "strategy must be auto, blur, mosaic or cover") String strategy,
            @RequestParam(name = "privacyLevel", defaultValue = "standard")
            @Pattern(regexp = "light|standard|strict", message = "privacyLevel must be light, standard or strict") String privacyLevel,
            @RequestParam(defaultValue = "normal")
            @Pattern(regexp = "low|normal|high", message = "priority must be low, normal or high") String priority
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(taskQueueService.submitBatch(files, strategy, privacyLevel, priority));
    }

    @GetMapping
    public List<TaskSummary> recent() {
        return taskService.recent();
    }

    @GetMapping("/stats")
    public TaskStatistics statistics() {
        return taskService.statistics();
    }

    @GetMapping("/{id}")
    public TaskView find(@PathVariable long id) {
        return taskService.find(id);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskSummary> retry(@PathVariable long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(taskQueueService.retry(id));
    }
}
