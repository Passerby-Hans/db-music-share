package com.music.common.session;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 会话服务：基于 Redis 的有状态登录态管理。
 *
 * <p>本系统鉴权采用「Session + Redis」而非 JWT，核心诉求是
 * <b>登出/封禁可即时作废</b>（详见设计方案决策④）。本类封装了
 * 会话的创建、读取、续期与删除四类操作。</p>
 *
 * <p>Redis key 形如 {@code session:<sessionId>}，value 为
 * 序列化后的 {@link LoginUser}，TTL 为 {@link #SESSION_TTL}。</p>
 */
@Service
public class SessionService {

    /** Redis 中会话 key 的统一前缀，便于归类与排查。 */
    private static final String KEY_PREFIX = "session:";

    /** 会话有效期：24 小时；每次访问会刷新（活跃续期）。 */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    /**
     * 操作 Redis 的模板。
     * 使用 {@code <String, Object>} 泛型，value 经 JSON 序列化，
     * 可直接存取 {@link LoginUser} 对象（序列化器见 RedisConfig）。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造器注入 RedisTemplate。
     *
     * @param redisTemplate 已配置 JSON 序列化的 Redis 模板
     */
    public SessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 创建会话：生成随机 sessionId，并将登录态写入 Redis。
     *
     * @param loginUser 待存储的登录态
     * @return 新生成的 sessionId（无意义随机串，下发给前端作为令牌）
     */
    public String createSession(LoginUser loginUser) {
        // UUID 去掉连字符，作为不可猜测的会话标识
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(buildKey(sessionId), loginUser, SESSION_TTL);
        return sessionId;
    }

    /**
     * 根据 sessionId 取回登录态。
     *
     * @param sessionId 前端携带的会话标识
     * @return 对应的登录态；不存在或已过期时返回 {@code null}
     */
    public LoginUser getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Object val = redisTemplate.opsForValue().get(buildKey(sessionId));
        return (val instanceof LoginUser) ? (LoginUser) val : null;
    }

    /**
     * 刷新会话 TTL（活跃续期）。
     *
     * <p>在拦截器校验通过后调用，使「闲置 24h 才过期、持续活跃则不过期」。</p>
     *
     * @param sessionId 会话标识
     */
    public void refreshSession(String sessionId) {
        redisTemplate.expire(buildKey(sessionId), SESSION_TTL);
    }

    /**
     * 删除会话（用于登出，使令牌即时失效）。
     *
     * @param sessionId 会话标识
     */
    public void deleteSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            redisTemplate.delete(buildKey(sessionId));
        }
    }

    /**
     * 拼接完整的 Redis key。
     *
     * @param sessionId 会话标识
     * @return 形如 {@code session:<sessionId>} 的完整 key
     */
    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
