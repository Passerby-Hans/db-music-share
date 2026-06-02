package com.music.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 模板配置。
 *
 * <p>默认的 RedisTemplate 用 JDK 序列化，存进 Redis 的是不可读的二进制。
 * 本配置改为：key 用字符串序列化（可读，如 {@code session:abc...}），
 * value 用 JSON 序列化，从而能直接存取 {@code LoginUser} 等对象。</p>
 *
 * <p>JSON 序列化器开启了类型信息写入，使反序列化时能还原为原始类型
 * （{@code GenericJackson2JsonRedisSerializer} 会在 JSON 中记录类名）。</p>
 */
@Configuration
public class RedisConfig {

    /**
     * 注册自定义的 {@link RedisTemplate}。
     *
     * @param connectionFactory 由 Spring Boot 自动配置的连接工厂
     * @return 配置好序列化器的 Redis 模板
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key / hashKey 用字符串序列化，保证 Redis 中 key 可读
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value / hashValue 用 JSON 序列化，支持对象存取
        GenericJackson2JsonRedisSerializer jsonSerializer = buildJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构建带类型信息的 JSON 序列化器。
     *
     * <p>开启默认类型标记后，序列化结果会内嵌类名，反序列化可精确还原对象类型，
     * 避免读出来变成 LinkedHashMap。</p>
     *
     * @return JSON 序列化器
     */
    private GenericJackson2JsonRedisSerializer buildJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        // 允许访问所有字段（含私有），无需 getter/setter 也能序列化
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 写入类型信息，使反序列化能还原具体类型
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
