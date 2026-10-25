package ch.dboeckli.example.otel.test.config;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class InMemoryTracingConfig {

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
