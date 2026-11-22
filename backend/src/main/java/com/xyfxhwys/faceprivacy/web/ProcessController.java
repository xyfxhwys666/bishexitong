package com.xyfxhwys.faceprivacy.web;

import com.xyfxhwys.faceprivacy.task.TaskService;
import com.xyfxhwys.faceprivacy.task.TaskView;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/process")
public class ProcessController {

    private final TaskService taskService;

    public ProcessController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskView> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "mosaic")
            @Pattern(regexp = "auto|blur|mosaic|cover", message = "strategy must be auto, blur, mosaic or cover") String strategy,
            @RequestParam(name = "privacyLevel", defaultValue = "standard")
            @Pattern(regexp = "light|standard|strict", message = "privacyLevel must be light, standard or strict") String privacyLevel
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        return ResponseEntity.ok(taskService.process(file, strategy, privacyLevel));
    }
}
