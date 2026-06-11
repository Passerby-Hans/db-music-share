package com.music.common.session;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 会话服务：基于 Redis 的有状态登录态管理。
 *
 * <p>本系统鉴权采用「Session + Redis」而非 JWT，核心诉求是
 * <b>登出/封禁可即时作废</b>（详见设计方案决策④）。本类封装了
 * 会话的创建、读取、续期与删除四类操作。</p>
 *
 * <p>Redis 结构有两套，互为补充：</p>
 * <ul>
 *   <li><b>正向</b> {@code session:<sessionId>} → 序列化的 {@link LoginUser}，
 *       带 {@link #SESSION_TTL}，是鉴权读取的主体；</li>
 *   <li><b>反向索引</b> {@code user_sessions:<uid>} → 该用户当前全部 sessionId 的 SET，
 *       用于「封禁/改角色/改密码时一次作废某用户所有会话」（见 {@link #deleteSessionsByUid}）。
 *       没有反向索引就只能凭 sessionId 删单个会话，无法按 uid 批量失效。</li>
 * </ul>
 *
 * <p><b>一致性说明</b>：反向索引集合不设 TTL（SET 无法对单成员设过期），
 * 因此正向会话因 24h 闲置过期后，其 sessionId 可能仍残留在索引集合里（陈旧成员）。
 * 这不影响正确性——{@link #deleteSessionsByUid} 删除时对已不存在的正向 key 是无害幂等，
 * 且每次删除后会重建/清空集合。极端长期残留由「集合被清空时顺手 del」收敛。</p>
 */
@Service
public class SessionService {

    /** Redis 中会话 key 的统一前缀，便于归类与排查。 */
    private static final String KEY_PREFIX = "session:";

    /** uid→sessionId 集合的反向索引 key 前缀。 */
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";

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
     * 创建会话：生成随机 sessionId，写入正向会话，并登记到该用户的反向索引集合。
     *
     * @param loginUser 待存储的登录态（必须含 uid，用于反向索引）
     * @return 新生成的 sessionId（无意义随机串，下发给前端作为令牌）
     */
    public String createSession(LoginUser loginUser) {
        // UUID 去掉连字符，作为不可猜测的会话标识
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(buildKey(sessionId), loginUser, SESSION_TTL);
        // 登记反向索引：uid → sessionId，供按 uid 批量作废（封禁/改角色/改密码）
        redisTemplate.opsForSet().add(buildUserKey(loginUser.getUid()), sessionId);
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
     * <p>除删正向会话外，还会把该 sessionId 从其所属用户的反向索引集合中摘除，
     * 避免索引里残留已登出的陈旧成员。</p>
     *
     * @param sessionId 会话标识
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        // 先读出登录态拿到 uid，用于同步清理反向索引（读不到则仅删正向 key）
        LoginUser loginUser = getSession(sessionId);
        redisTemplate.delete(buildKey(sessionId));
        if (loginUser != null && loginUser.getUid() != null) {
            redisTemplate.opsForSet().remove(buildUserKey(loginUser.getUid()), sessionId);
        }
    }

    /**
     * 作废某用户的<b>全部</b>会话（封禁、改角色、改密码后调用，使其已登录设备即时失效）。
     *
     * <p>从反向索引集合取出该用户当前所有 sessionId，逐个删除正向会话，
     * 最后删除索引集合本身。对已过期/不存在的 sessionId 删除无害（幂等）。
     * 这是「封禁立即生效」需求的落地能力——没有它，被封用户手中的旧令牌仍可继续访问。</p>
     *
     * @param uid 目标用户 uid；为 {@code null} 时直接返回
     */
    public void deleteSessionsByUid(Long uid) {
        if (uid == null) {
            return;
        }
        String userKey = buildUserKey(uid);
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userKey);
        if (sessionIds != null) {
            for (Object sid : sessionIds) {
                // 成员以字符串存入，直接删除对应正向会话 key
                redisTemplate.delete(buildKey(sid.toString()));
            }
        }
        // 索引集合整体清除，避免残留陈旧成员
        redisTemplate.delete(userKey);
    }

    /**
     * 拼接完整的 Redis 正向会话 key。
     *
     * @param sessionId 会话标识
     * @return 形如 {@code session:<sessionId>} 的完整 key
     */
    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    /**
     * 拼接用户反向索引集合的 Redis key。
     *
     * @param uid 用户 uid
     * @return 形如 {@code user_sessions:<uid>} 的完整 key
     */
    private String buildUserKey(Long uid) {
        return USER_SESSIONS_PREFIX + uid;
    }
}
