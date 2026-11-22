package com.xyfxhwys.faceprivacy.web;

import com.xyfxhwys.faceprivacy.audit.AuditLogService;
import com.xyfxhwys.faceprivacy.auth.AuthSessionService;
import com.xyfxhwys.faceprivacy.task.TaskService;
import com.xyfxhwys.faceprivacy.task.TaskSummary;
import com.xyfxhwys.faceprivacy.task.BatchView;
import com.xyfxhwys.faceprivacy.task.TaskQueueService;
import com.xyfxhwys.faceprivacy.task.TaskView;
import com.xyfxhwys.faceprivacy.task.TaskStatistics;
import jakarta.validation.constraints.Pattern;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuthSessionService authSessionService;
    private final AuditLogService auditLogService;

    public TaskController(
            TaskService taskService,
            TaskQueueService taskQueueService,
            AuthSessionService authSessionService,
            AuditLogService auditLogService
    ) {
        this.taskService = taskService;
        this.taskQueueService = taskQueueService;
        this.authSessionService = authSessionService;
        this.auditLogService = auditLogService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskSummary> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "mosaic")
            @Pattern(regexp = "auto|blur|mosaic|cover", message = "strategy must be auto, blur, mosaic or cover") String strategy,
            @RequestParam(name = "privacyLevel", defaultValue = "standard")
            @Pattern(regexp = "light|standard|strict", message = "privacyLevel must be light, standard or strict") String privacyLevel,
            @RequestParam(defaultValue = "normal")
            @Pattern(regexp = "low|normal|high", message = "priority must be low, normal or high") String priority,
            HttpServletRequest request
    ) {
        AuthSessionService.Session session = authSessionService.require(request);
        TaskSummary summary = taskQueueService.submit(file, strategy, privacyLevel, priority, session.username());
        auditLogService.record(session, "CREATE_TASK", "TASK", String.valueOf(summary.id()),
                "strategy=" + strategy + ", privacyLevel=" + privacyLevel + ", priority=" + priority);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(summary);
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchView> createBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "mosaic")
            @Pattern(regexp = "auto|blur|mosaic|cover", message = "strategy must be auto, blur, mosaic or cover") String strategy,
            @RequestParam(name = "privacyLevel", defaultValue = "standard")
            @Pattern(regexp = "light|standard|strict", message = "privacyLevel must be light, standard or strict") String privacyLevel,
            @RequestParam(defaultValue = "normal")
            @Pattern(regexp = "low|normal|high", message = "priority must be low, normal or high") String priority,
            HttpServletRequest request
    ) {
        AuthSessionService.Session session = authSessionService.require(request);
        BatchView batch = taskQueueService.submitBatch(files, strategy, privacyLevel, priority, session.username());
        auditLogService.record(session, "CREATE_BATCH", "BATCH", null,
                "count=" + batch.total() + ", strategy=" + strategy + ", privacyLevel=" + privacyLevel);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(batch);
    }

    @GetMapping
    public List<TaskSummary> recent(HttpServletRequest request) {
        return taskService.recent(authSessionService.require(request));
    }

    @GetMapping("/stats")
    public TaskStatistics statistics(HttpServletRequest request) {
        return taskService.statistics(authSessionService.require(request));
    }

    @GetMapping("/{id}")
    public TaskView find(@PathVariable long id, HttpServletRequest request) {
        return taskService.find(id, authSessionService.require(request));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskSummary> retry(@PathVariable long id, HttpServletRequest request) {
        AuthSessionService.Session session = authSessionService.require(request);
        TaskSummary summary = taskQueueService.retry(id, session);
        auditLogService.record(session, "RETRY_TASK", "TASK", String.valueOf(id), "任务重新排队");
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(summary);
    }
}
