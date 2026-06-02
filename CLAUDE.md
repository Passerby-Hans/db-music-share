# CLAUDE.md — 在线音乐分享系统（数据库课程设计）

## 协作规则（最高优先级）
- **涉及方案 / 技术决策时，必须先把方案列出来让我过目、得到确认后再动手写代码。**
  不要擅自定方案就直接实现。决策范围包括但不限于：技术选型、依赖引入、
  架构与分层、模块设计、接口设计、数据流/缓存方案。

## 代码注释规则
- 写代码时必须配**很详细、很规范**的注释：类有类注释（职责说明），
  方法有方法注释（功能、参数、返回值、异常），关键逻辑行内说明 WHY。
- Java 用标准 Javadoc 风格（`/** ... */`，`@param`/`@return`/`@throws`）。

## 代码组织约定
- 后端**按模块分包**：`com.music.<模块名>`（如 `user/`、`song/`、`album/` …），
  每个模块内部各自包含 `controller` / `service` / `mapper` / `entity` 等分层。
- 三层分工（controller → service → mapper）必须保留，不可合并。
- 公共基础设施（统一返回体、全局异常、JWT、拦截器、配置等）放在 `com.music.common`。

## 技术栈（已定）
- 后端：Spring Boot 3.4.5 + JDK 21 + MyBatis-Plus 3.5.16
- 数据库：PostgreSQL 18；缓存：Redis 8（排行榜 ZSET + 会话管理）
- 鉴权：Session + Redis（有状态令牌）。登录发随机 sessionId，用户态存 Redis；
  登出/封禁可即时作废。**不用 JWT**（无状态无法即时失效，不满足封禁立即生效需求）。
  Session TTL 24h，每次访问刷新（活跃续期）。
- 前端：Vue3（前后端分离）
- 连接配置见 `docker-compose.dev.yml` 与 `backend/src/main/resources/application.yml`
- 构建注意：系统 `JAVA_HOME` 可能指向旧 JDK，构建前需确保用 JDK 21
