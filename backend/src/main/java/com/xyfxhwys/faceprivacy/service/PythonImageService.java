package com.xyfxhwys.faceprivacy.service;

import java.io.IOException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PythonImageService {

    private final RestClient restClient;

    public PythonImageService(RestClient pythonRestClient) {
        this.restClient = pythonRestClient;
    }

    public String process(MultipartFile file, String strategy, String privacyLevel) {
        try {
            return process(file.getBytes(), file.getOriginalFilename(), strategy, privacyLevel);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read uploaded file", ex);
        }
    }

    public String process(byte[] bytes, String filename, String strategy, String privacyLevel) {
        var resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "upload.jpg" : filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("strategy", strategy);
        body.add("privacy_level", privacyLevel);
        return restClient.post()
                .uri("/api/v1/process")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
