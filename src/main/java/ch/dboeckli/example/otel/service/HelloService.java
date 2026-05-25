package ch.dboeckli.example.otel.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HelloService {

    public final static String HELLO_MESSAGE_FROM_SERVICE = "Service Sais Hello...";

    private final Tracer tracer;

    public HelloService(OpenTelemetry openTelemetry, @Value("${spring.application.name}") String appName,
            BuildProperties buildProperties) {
        this.tracer = openTelemetry.getTracer(appName, buildProperties.getVersion());
    }

    public String processHello() {
        // Create a span for the entire service processing operation
        Span serviceSpan = tracer.spanBuilder("process-hello")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("addedSpanAttributeByService", "helloFromService")
            .startSpan();

        // Make the span current for this execution context
        try (Scope _s = serviceSpan.makeCurrent()) {
            // Log events within the span
            serviceSpan.addEvent("service-started");

            try {
                serviceSpan.addEvent("service-completed");
                serviceSpan.setStatus(StatusCode.OK);
                return "Hello from the service";
            }
            catch (Exception e) {
                serviceSpan.recordException(e);
                throw e;
            }
        }
        finally {
            serviceSpan.end();
        }
    }

}
