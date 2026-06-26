# 在线音乐分享系统

> 数据库课程设计 —— 支持用户登录、歌曲上传与审核、在线播放、收藏 / 歌单 / 评论 / 评分、排行榜的在线音乐分享平台。
>
> 后端 Spring Boot 3 + JDK 21，数据库 PostgreSQL 18，缓存 Redis 8，对象存储 MinIO；前端 Vue3。
> 鉴权采用 **Session + Redis（有状态令牌）**：登录发随机 sessionId 存 Redis，封禁 / 登出可即时作废。

---

## 一、项目简介

### 功能概览

- **用户**：注册 / 登录 / 登出 / 改密 / 头像；多角色（普通用户 / 上传者 / 管理员）；封禁即时生效。
- **歌曲**：上传、审核（管理员通过 / 驳回）、下架；在线播放（循环 / 顺序 / 随机 / 单曲）。
- **社交**：收藏、歌单（自建 / 管理）、评论（含点赞）、评分。
- **榜单**：基于播放量的 Redis ZSET 排行榜。
- **管理**：歌曲管理 + 审核合并页、用户管理。

### 数据库（10 张表）

用户 `app_user`；专辑 / 歌曲 `album`、`song`；行为 `play_record`、`favorite`、`rating`、`comment`、`comment_like`；歌单 `playlist`、`playlist_detail`。

> 建表脚本 `sql/01_schema.sql`，索引 `sql/02_indexes.sql`。

### 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.4.5 · JDK 21 · MyBatis-Plus 3.5.16 · MinIO SDK 8.5.17 |
| 数据库 / 缓存 | PostgreSQL 18 · Redis 8 |
| 对象存储 | MinIO（音频私有桶 + 封面公开桶） |
| 鉴权 | Session + Redis（`X-Token` 请求头，TTL 24h，访问即续期） |
| 前端 | Vue 3 · Vite 6 · Element Plus · Pinia · Vue Router · Tailwind CSS · ECharts |
| 包管理 / 部署 | Maven · pnpm 10 · Docker Compose（多阶段镜像构建） |

---

## 二、环境要求

### 方式 A：一键 Docker 部署（推荐）

- **Docker**（含 BuildKit）
- **Docker Compose** v2（`docker compose` 子命令）

> 容器内构建已配置国内镜像源（Maven 走阿里云、pnpm 走 npmmirror、apt 走阿里云），国内网络下首次构建亦可正常完成。

### 方式 B：本地开发

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **21** | 系统 `JAVA_HOME` 可能指向旧版，构建前确认 `java -version` 为 21 |
| Maven | 3.9+ | 后端编译运行 |
| Node.js | 20+ | 前端构建 |
| pnpm | 10 | 前端包管理（`corepack enable` 即用） |
| Docker + Compose | — | 起 PostgreSQL / Redis / MinIO 依赖 |

---

## 三、快速部署

> 适用 `docker-compose.yml`：5 服务整栈编排（`postgres` / `redis` / `minio` / `backend` / `nginx`），一键拉起。

### 1. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，**对外访问须改 `PUBLIC_HOST`**：

```dotenv
PUBLIC_HOST=192.168.0.4      # ← 改为访问本机的 IP / 域名（仅本机访问可保持 localhost）
APP_PORT=8080                # nginx 应用端口
MINIO_PORT=9000              # nginx 直透 MinIO 的端口
POSTGRES_PASSWORD=music123        # 数据库密码
MINIO_ROOT_PASSWORD=music123456   # MinIO 密码（≥8 位）
```

### 2. 构建 + 启动

```bash
docker compose up --build -d
```

- `--build`：首次运行或改动源码 / 配置后须带此参数重建镜像。
- 数据库为**干净库**（仅含表结构与索引）。

### 3. 查看启动状态

```bash
docker compose ps
```

等待 `backend` 健康检查列为 `healthy`（`start_period` 约 40s，含首次依赖下载）。

### 4. 访问

浏览器打开 `http://<PUBLIC_HOST>:<APP_PORT>`（默认 `http://localhost:8080`）。

---

## 四、配置参考

### 发行版环境变量（`.env`）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PUBLIC_HOST` | `localhost` | 浏览器访问本系统的 host（IP / 域名）。决定预签名 URL 与封面直链的 host，对外访问必改 |
| `APP_PORT` | `8080` | nginx 对外端口（应用首页） |
| `MINIO_PORT` | `9000` | nginx 直透 MinIO 的端口（音频 / 封面） |
| `POSTGRES_PASSWORD` | `music123` | PostgreSQL `music` 用户密码（同时注入 backend 数据源） |
| `MINIO_ROOT_PASSWORD` | `music123456` | MinIO root 密码（即 secret key，≥8 位） |

> `docker-compose.yml` 各变量均带 `${VAR:-default}`，不建 `.env` 也能以默认值运行；建了 `.env` 才会被覆盖。

### 后端关键配置（`backend/src/main/resources/application.yml`）

| 配置项 | 值（开发态默认） | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| 数据源 | `localhost:5432/music_share`，`music/music123` | 对应 dev 依赖 |
| Redis | `localhost:6380` | 对应 dev 依赖映射 |
| MinIO endpoint | `http://localhost:9000` | SDK 访问地址 |
| `storage.minio.audio-bucket` | `music-audio` | 音频私有桶（预签名 URL） |
| `storage.minio.cover-bucket` | `music-cover` | 封面公开桶（直链） |
| `storage.minio.presigned-expiry-seconds` | `7200` | 音频预签名 URL 有效期（2 小时） |
| `spring.servlet.multipart.max-file-size` | `100MB` | 单文件上限（支持无损 flac） |

> 发行版中 PG / Redis / MinIO 连接信息由 `docker-compose.yml` 的 `environment` 注入覆盖（指向容器名 `postgres` / `redis` / `minio`）。两个 MinIO 桶由 **backend 启动时自动创建**，无需手动建桶。

### 端口

| 端口 | 环境 | 归属 |
| --- | --- | --- |
| `APP_PORT`（默认 8080） | 发行版 | nginx（应用首页 + `/api`） |
| `MINIO_PORT`（默认 9000） | 发行版 | nginx（透传 MinIO 音频 / 封面） |

---

## 五、目录结构

```
.
├── backend/                     后端 Spring Boot（Java 21）
│   ├── src/main/java/com/music/        按模块分包（common / user / song / album …）
│   ├── src/main/resources/application.yml
│   ├── Dockerfile               多阶段：Maven 构建 → JRE21 运行
│   ├── settings.xml             容器内阿里云 Maven 镜像
│   └── pom.xml
├── frontend/                    前端 Vue3 + Vite
│   ├── src/
│   ├── Dockerfile               多阶段：pnpm 构建 → nginx 托管
│   ├── nginx.conf               /api 反代 + MinIO :9000 直透
│   └── package.json
├── sql/                         数据库脚本（建表 + 索引）
│   ├── 01_schema.sql
│   └── 02_indexes.sql
├── docker-compose.yml           ★ 发行版 5 服务编排
├── docker-compose.dev.yml       本地开发依赖（PG / Redis / MinIO）
├── .env.example                 发行版可调参数模板
└── README.md                    本文件
```

> `docs/`（设计文档、接口文档、开发日志）与 `CLAUDE.md` 为本地协作资料，不随仓库分发。
