package ch.dboeckli.example.otel.rest;

import ch.dboeckli.example.otel.service.HelloService;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController {

    protected final static String HELLO_MESSAGE = "Say Hello...";
    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.setAttribute("app.custom.controller.flag", "hello");
        }
        log.info(HELLO_MESSAGE);
        helloService.processHello();

        return new ResponseEntity<>("{\"message\":\"hello\"}", HttpStatus.OK);
    }
}
