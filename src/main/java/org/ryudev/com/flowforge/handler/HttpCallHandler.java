package org.ryudev.com.flowforge.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.domain.StepType;
import org.ryudev.com.flowforge.exception.StepExecutionException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
class HttpCallHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.HTTP_CALL;
    }

    @Override
    public Object execute(StepDefinition step, Map<String, Object> previousOutputs) throws Exception {
        Map<String, Object> config = step.getConfig();
        String url = (String) config.get("url");
        String method = ((String) config.getOrDefault("method", "GET")).toUpperCase();

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());
        String body = (String) config.get("body");
        int timeoutSeconds = step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 30;

        log.debug("HTTP {} {}", method, url);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpUriRequestBase request = buildRequest(method, url, body);
            headers.forEach(request::addHeader);

            return client.execute(request, response -> {
                int statusCode = response.getCode();
                String responseBody = response.getEntity() != null
                        ? new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8)
                        : "";

                if (statusCode >= 400) {
                    throw new StepExecutionException(
                            "HTTP " + statusCode + " from " + url + ": " + responseBody);
                }

                return Map.of(
                        "statusCode", statusCode,
                        "body", responseBody
                );
            });
        }
    }

    private HttpUriRequestBase buildRequest(String method, String url, String body) {
        return switch (method) {
            case "GET" -> new HttpGet(url);
            case "POST" -> buildWithBody(new HttpPost(url), body);
            case "PUT" -> buildWithBody(new HttpPut(url), body);
            case "PATCH" -> buildWithBody(new HttpPatch(url), body);
            case "DELETE" -> new HttpDelete(url);
            default -> throw new StepExecutionException("Unsupported HTTP method: " + method);
        };
    }

    private HttpUriRequestBase buildWithBody(HttpUriRequestBase request, String body) {
        if (body != null && !body.isBlank()) {
            HttpUriRequestBase request1 = request;
            if (request instanceof HttpPost post) {
                post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            } else if (request instanceof HttpPut put) {
                put.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            } else if (request instanceof HttpPatch patch) {
                patch.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            }
        }
        return request;
    }
}
