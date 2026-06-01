package com.music.controller;

import com.music.mapper.PingMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    private final PingMapper pingMapper;
    private final StringRedisTemplate redis;

    public PingController(PingMapper pingMapper, StringRedisTemplate redis) {
        this.pingMapper = pingMapper;
        this.redis = redis;
    }

    @GetMapping
    public Map<String, Object> ping() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 探测 PostgreSQL
        try {
            long songs = pingMapper.countSongs();
            result.put("postgres", "ok, song count = " + songs);
        } catch (Exception e) {
            result.put("postgres", "FAIL: " + e.getMessage());
        }

        // 探测 Redis
        try {
            redis.opsForValue().set("ping:test", "pong");
            String v = redis.opsForValue().get("ping:test");
            result.put("redis", "ok, value = " + v);
        } catch (Exception e) {
            result.put("redis", "FAIL: " + e.getMessage());
        }

        return result;
    }
}
