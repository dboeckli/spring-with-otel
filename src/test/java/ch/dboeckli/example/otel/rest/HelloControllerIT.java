package ch.dboeckli.example.otel.rest;


import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static ch.dboeckli.example.otel.rest.HelloController.HELLO_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class HelloControllerIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void hello_returnsHelloMessage() {
        String url = "http://localhost:" + port + "/hello";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertAll(
            () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(response.getBody()).isEqualTo("{\"message\":\"hello\"}")
        );
    }

    @Test
    void hello_logsMessage() {
        try (LogCaptor logCaptor = LogCaptor.forClass(HelloController.class)) {
            String url = "http://localhost:" + port + "/hello";
            restTemplate.getForEntity(url, String.class);

            List<LogEvent> logEvents =  logCaptor.getLogEvents();

            assertAll(
                () -> assertNotNull(logEvents),
                () -> assertEquals(1, logEvents.size()),
                () -> assertEquals(HelloController.class.getName(), logEvents.getFirst().getLoggerName()),
                () -> assertEquals(HELLO_MESSAGE, logEvents.getFirst().getMessage()),
                () -> assertThat(logEvents.getFirst().getDiagnosticContext().get("trace_id")).isNotBlank().matches("[0-9a-f]{32}"),
                () -> assertThat(logEvents.getFirst().getDiagnosticContext().get("span_id")).as("span_id").isNotBlank().matches("[0-9a-f]{16}"),
                () -> assertThat(logEvents.getFirst().getDiagnosticContext().get("trace_flags")).as("trace_flags").isNotBlank().matches("[0-9a-f]{2}")
            );
        }
    }

    @Test
    void hello_logsMessage_viaLogbackAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(HelloController.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        String url = "http://localhost:" + port + "/hello";
        restTemplate.getForEntity(url, String.class);
        List<ILoggingEvent> logEvents = listAppender.list;

        assertAll(
            () -> assertNotNull(logEvents),
            () -> assertEquals(1, logEvents.size()),
            () -> assertThat(logEvents.getFirst().getFormattedMessage()).contains(HelloController.HELLO_MESSAGE),
            () -> assertThat(logEvents.getFirst().getMDCPropertyMap().get("trace_id")).isNotBlank().matches("[0-9a-f]{32}"),
            () -> assertThat(logEvents.getFirst().getMDCPropertyMap().get("span_id")).as("span_id").isNotBlank().matches("[0-9a-f]{16}"),
            () -> assertThat(logEvents.getFirst().getMDCPropertyMap().get("trace_flags")).as("trace_flags").isNotBlank().matches("[0-9a-f]{2}")
        );

        logger.detachAppender(listAppender);
        listAppender.stop();
    }

}