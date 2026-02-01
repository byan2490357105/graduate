package com.billbill2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局JSON时间格式化配置
 */
@Configuration
public class JacksonConfig {

    // 匹配Python发送的时间格式：YYYY-MM-DD HH:MM:SS
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        JavaTimeModule module = new JavaTimeModule();

        // 反序列化：String→LocalDateTime
        LocalDateTimeDeserializer deserializer = new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT));
        module.addDeserializer(LocalDateTime.class, deserializer);

        // 序列化：LocalDateTime→String（可选）
        LocalDateTimeSerializer serializer = new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT));
        module.addSerializer(LocalDateTime.class, serializer);

        objectMapper.registerModule(module);
        return objectMapper;
    }
}