package com.example.smsbackend.config;

import com.example.smsbackend.json.FlexibleBooleanDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule flexibleBooleanModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Boolean.class, new FlexibleBooleanDeserializer());
        module.addDeserializer(boolean.class, new FlexibleBooleanDeserializer());
        return module;
    }
}

