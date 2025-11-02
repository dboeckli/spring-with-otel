package ch.dboeckli.example.otel.observability;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
        "spring.docker.compose.skip.in-tests=false"
    }
)
@ActiveProfiles("local")
@Slf4j
@AutoConfigureObservability
class MetricsWithPrometheusIT {
    @Autowired
    TestRestTemplate restTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void prometheus_server_has_metrics_after_hello_call() {
        String prometheus = "http://localhost:9090/api/v1/query";
        String httpQuery = prometheus + "?query=http_server_requests_seconds_count";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> httpMetric =
            await()
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ofSeconds(2))
                .pollInterval(Duration.ofSeconds(2))
                .conditionEvaluationListener(condition -> log.info("Polling Prometheus ({}): {}", condition.getRemainingTimeInMS(), httpQuery))
                .until(() -> restTemplate.exchange(httpQuery, HttpMethod.GET, request, String.class),
                    resp -> resp.getStatusCode().is2xxSuccessful()
                        && resp.getBody() != null
                        && resp.getBody().contains("\"status\":\"success\"")
                        && resp.getBody().contains("\"resultType\":\"vector\"")
                        && resp.getBody().contains("\"metric\""));

        log.info("Prometheus response for http_server_requests_seconds_count: {}", this.pretty(httpMetric.getBody()));
    }

    private String pretty(String body) {
        try {
            Object json = OBJECT_MAPPER.readValue(body, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            // Falls kein valides JSON: unverändert zurückgeben
            return body;
        }
    }

}