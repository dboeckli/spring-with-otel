package ch.dboeckli.example.otel.rest;


import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.docker.compose.skip.in-tests=false"
    }
)
@ActiveProfiles("local")
@Slf4j
class HelloControllerWithPrometheusIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void prometheus_server_has_metrics_after_hello_call() throws InterruptedException {
        String prometheus = "http://localhost:9090/api/v1/query";
        String httpQuery = prometheus + "?query=http_server_requests_seconds_count";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> httpMetric =
            await()
                .atMost(Duration.ofSeconds(200))
                .pollDelay(Duration.ofSeconds(1))
                .pollInterval(Duration.ofSeconds(1))
                .conditionEvaluationListener(condition -> {
                    log.debug("Polling Prometheus ({}): {}", condition.getRemainingTimeInMS(), httpQuery);
                })
                .until(() -> restTemplate.exchange(httpQuery, HttpMethod.GET, request, String.class),
                    resp -> resp.getStatusCode().is2xxSuccessful()
                        && resp.getBody() != null
                        && resp.getBody().contains("\"status\":\"success\"")
                        && resp.getBody().contains("\"resultType\":\"vector\"")
                        && resp.getBody().contains("\"metric\""));

        log.info("Prometheus response for http_server_requests_seconds_count: {}", httpMetric.getBody());


    }

}