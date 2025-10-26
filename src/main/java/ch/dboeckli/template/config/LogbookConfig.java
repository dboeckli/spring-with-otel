package ch.dboeckli.template.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Sink;
import org.zalando.logbook.json.JsonHttpLogFormatter;
import org.zalando.logbook.logstash.LogstashLogbackSink;

@Configuration
public class LogbookConfig {

    @Bean
    public Sink logbookLogstashSink() {
        return new LogstashLogbackSink(new JsonHttpLogFormatter());
    }

}
