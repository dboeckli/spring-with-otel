package ch.dboeckli.example.otel.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class TraceDebugFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Log incoming traceparent header
        String incomingTraceparent = request.getHeader("traceparent");
        log.info("### Incoming traceparent: {}", incomingTraceparent);

        // Log current span context
        SpanContext traceContext = Span.current().getSpanContext();
        if (traceContext.isValid()) {
            log.info("### Current trace context: {}",
                    ReflectionToStringBuilder.toString(traceContext, ToStringStyle.MULTI_LINE_STYLE));
        }
        else {
            log.warn("### No valid span context found: {}",
                    ReflectionToStringBuilder.toString(traceContext, ToStringStyle.MULTI_LINE_STYLE));
        }

        filterChain.doFilter(request, response);
    }

}
