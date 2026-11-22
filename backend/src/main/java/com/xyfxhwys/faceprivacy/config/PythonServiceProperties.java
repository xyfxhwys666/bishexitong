package com.xyfxhwys.faceprivacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "python-service")
public record PythonServiceProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutSeconds
) {
}
