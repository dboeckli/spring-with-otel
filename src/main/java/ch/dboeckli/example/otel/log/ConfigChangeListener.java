package ch.dboeckli.example.otel.log;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
@Slf4j
public class ConfigChangeListener {

    private final OpenTelemetry openTelemetry;

    public ConfigChangeListener(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    private static final List<String> PASSWORD_KEY_LIST = Arrays.asList("jwt.key-value", "password", "credentials",
            "secret");

    @EventListener
    @WithSpan(value = "config.change.listener", kind = SpanKind.INTERNAL)
    public void doHandleContextRefresh(ContextRefreshedEvent event) {
        final Environment env = event.getApplicationContext().getEnvironment();
        log.debug(LogMessage.RECEIVED_CONTEXT_REFRESH_EVENT.getMessage());
        log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
        final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
        StreamSupport.stream(sources.spliterator(), false)
            .filter(EnumerablePropertySource.class::isInstance)
            .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
            .flatMap(Arrays::stream)
            .distinct()
            .forEach(prop -> {
                String propertyValue = env.getProperty(prop);
                if (propertyValue != null) {

                    if (PASSWORD_KEY_LIST.stream().anyMatch(prop.toLowerCase()::contains)
                            || PASSWORD_KEY_LIST.stream().anyMatch(propertyValue.toLowerCase()::contains)) {

                        log.info("{}: {}", prop, "**************************"); // hide
                                                                                // password
                    }
                    else {
                        log.info("{}: {}", prop, propertyValue);
                    }
                }
                else {
                    log.warn("null propertyValue encountered in {}: {}", prop, propertyValue);
                }
            });
        showTracerProvider();
    }

    private void showTracerProvider() {
        log.info("### OpenTelemetry impl: {}", openTelemetry.getClass().getName());
        log.info("### TracerProvider impl: {}", openTelemetry.getTracerProvider().getClass().getName());

        if (openTelemetry instanceof OpenTelemetrySdk sdk) {
            SdkTracerProvider provider = sdk.getSdkTracerProvider();
            log.info("### SdkTracerProvider: {}",
                    ReflectionToStringBuilder.toString(provider, ToStringStyle.MULTI_LINE_STYLE));

            try {
                // sharedState holen
                Field sharedStateField = SdkTracerProvider.class.getDeclaredField("sharedState");
                sharedStateField.setAccessible(true);
                Object sharedState = sharedStateField.get(provider);

                // activeSpanProcessor holen
                Field processorField = sharedState.getClass().getDeclaredField("activeSpanProcessor");
                processorField.setAccessible(true);
                Object processor = processorField.get(sharedState);

                // Der BatchSpanProcessor enthält den OtlpHttpSpanExporter oder
                // OtlpGrpcSpanExporter
                log.info("### SpanProcessor: {}", processor.getClass().getName());
                log.info("### SpanProcessor detail: {}",
                        ReflectionToStringBuilder.toString(processor, ToStringStyle.MULTI_LINE_STYLE));

                // Worker aus BatchSpanProcessor holen
                Field workerField = processor.getClass().getDeclaredField("worker");
                workerField.setAccessible(true);
                Object worker = workerField.get(processor);

                // spanExporter aus Worker holen
                Field exporterField = worker.getClass().getDeclaredField("spanExporter");
                exporterField.setAccessible(true);
                Object exporter = exporterField.get(worker);

                log.info("### SpanExporter class: {}", exporter.getClass().getName());
                log.info("### SpanExporter detail: {}",
                        ReflectionToStringBuilder.toString(exporter, ToStringStyle.MULTI_LINE_STYLE));

            }
            catch (Exception e) {
                log.warn("### Could not inspect exporter: {}", e.getMessage());
            }
        }
    }

}
