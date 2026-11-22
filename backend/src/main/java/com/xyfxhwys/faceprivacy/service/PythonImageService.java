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
            var resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "upload.jpg" : file.getOriginalFilename();
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
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read uploaded file", ex);
        }
    }
}
