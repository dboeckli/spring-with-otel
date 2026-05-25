package ch.dboeckli.example.otel.rest;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none" })
@ActiveProfiles("local")
@Import(HelloControllerTest.TestTracingConfig.class)
@Slf4j
public class HelloControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    InMemorySpanExporter spanExporter;

    @Autowired
    ApplicationContext ctx;

    @TestConfiguration
    static class TestTracingConfig {

        @Bean
        public InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        public AutoConfigurationCustomizerProvider inMemoryTracerCustomizer(InMemorySpanExporter exporter) {
            return customizer -> customizer.addTracerProviderCustomizer(
                    (builder, config) -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter)));
        }

    }

    @BeforeEach
    void setUp() {
        log.info("### SpanProcessors: {}", Arrays.toString(ctx.getBeanNamesForType(SpanProcessor.class)));
        log.info("### SpanExporters: {}", Arrays.toString(ctx.getBeanNamesForType(SpanExporter.class)));
        spanExporter.reset();
    }

    @AfterEach
    void tearDown() {
        spanExporter.reset();
    }

    @Test
    void hello_returnsHelloMessage() throws IOException, InterruptedException {
        String traceParentTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String traceParentSpanId = "00f067aa0ba902b7";

        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/hello"))
                .GET()
                .header("Accept", "application/json")
                .header("traceparent", "00-" + traceParentTraceId + "-" + traceParentSpanId + "-01")
                .header("baggage", "testBaggage=hallo")
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertAll(() -> assertThat(response.statusCode()).isEqualTo(200),
                    () -> assertThat(response.body()).isEqualTo("{\"message\":\"hello\"}"));
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        assertAll(() -> assertThat(spans).hasSize(2),

                () -> {
                    Assertions.assertNotNull(spans);
                    SpanData span = spans.getFirst();
                    List<EventData> events = span.getEvents();

                    assertAll(
                            // Span‑Basics
                            () -> assertThat(span.getName()).isEqualTo("process-hello"),
                            () -> assertThat(span.getKind().name()).isEqualTo("INTERNAL"),
                            () -> assertThat(span.getStatus().getStatusCode().name()).isEqualTo("OK"),

                            // Events allgemein
                            () -> assertThat(events).hasSize(2),

                            // Event‑Namen / Reihenfolge
                            () -> {
                                Assertions.assertNotNull(events);
                                assertThat(events.getFirst().getName()).isEqualTo("service-started");
                            }, () -> {
                                Assertions.assertNotNull(events);
                                assertThat(events.get(1).getName()).isEqualTo("service-completed");
                            },

                            // Optional: zeitliche Reihenfolge
                            () -> {
                                Assertions.assertNotNull(events);
                                assertThat(events.get(1).getEpochNanos())
                                    .isGreaterThanOrEqualTo(events.get(0).getEpochNanos());
                            });
                });

    }

}
