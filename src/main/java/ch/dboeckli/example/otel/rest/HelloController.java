package ch.dboeckli.example.otel.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        log.info("Say Hello...");
        return new ResponseEntity<>("{\"message\":\"hello\"}", HttpStatus.OK);
    }
}
