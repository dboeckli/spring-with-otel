package ch.dboeckli.example.otel.rest;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.docker.compose.skip.in-tests=false"
    }
)@ActiveProfiles("local")
class HelloControllerWithPrometheusIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void prometheus_server_has_metrics_after_hello_call() throws InterruptedException {
        String helloUrl = "http://localhost:" + port + "/hello";
        restTemplate.getForEntity(helloUrl, String.class);

        Thread.sleep(1000);

        String prometheus = "http://localhost:9090/api/v1/query";

        String httpQuery = prometheus + "?query=http_server_requests_seconds_count";
        ResponseEntity<String> httpMetric = restTemplate.getForEntity(httpQuery, String.class);

        String logsQuery = prometheus + "?query=otel_logs_exporter_exported_log_records";
        ResponseEntity<String> logsMetric = restTemplate.getForEntity(logsQuery, String.class);

        assertAll(
            () -> assertThat(httpMetric.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(httpMetric.getBody()).contains("\"status\":\"success\""),

            () -> assertThat(httpMetric.getBody()).contains("\"resultType\":\"vector\""),
            () -> assertThat(httpMetric.getBody()).contains("\"metric\""),

            () -> assertThat(logsMetric.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(logsMetric.getBody()).contains("\"status\":\"success\"")
        );
    }

}