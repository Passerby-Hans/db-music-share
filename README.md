# 在线音乐分享系统

> 数据库课程设计 —— 一个支持用户注册登录、歌曲上传与审核、在线播放、收藏/歌单/评论/评分、排行榜的在线音乐分享平台。
>
> 后端 Spring Boot 3 + JDK 21，数据库 PostgreSQL 18，缓存 Redis 8，对象存储 MinIO；前端 Vue3。
> 鉴权采用 **Session + Redis（有状态令牌）**：登录发随机 sessionId 存 Redis，封禁/登出可即时作废。

---

## 目录

- [一、项目简介](#一项目简介)
- [二、系统架构](#二系统架构)
- [三、环境要求](#三环境要求)
- [四、快速部署（生产 / 演示）](#四快速部署生产--演示)
- [五、本地开发](#五本地开发)
- [六、配置参考](#六配置参考)
- [七、部署运维与常见问题](#七部署运维与常见问题)
- [八、目录结构](#八目录结构)
- [九、默认账号与演示说明](#九默认账号与演示说明)

---

## 一、项目简介

### 功能概览

- **用户**：注册 / 登录 / 登出 / 改密 / 头像；角色（普通用户 / 上传者 / 管理员）；封禁即时生效。
- **歌曲**：上传（音频走 MinIO 私有桶 + 预签名 URL，封面走公开桶直链）、审核（管理员通过/驳回）、下架。
- **播放**：在线播放、播放模式（循环/顺序/随机/单曲）、播放记录、播放栏一键收藏/加入歌单。
- **社交**：收藏、歌单（自建/管理）、评论（含点赞）、评分。
- **榜单**：基于播放量的 Redis ZSET 排行榜。
- **管理**：歌曲管理 + 审核合并页、用户管理。

### 数据库（10 张表）

| 模块 | 表 |
| --- | --- |
| 用户 | `app_user` |
| 专辑 / 歌曲 | `album`、`song` |
| 行为 | `play_record`、`favorite`、`rating`、`comment`、`comment_like` |
| 歌单 | `playlist`、`playlist_detail` |

> 建表脚本见 `sql/01_schema.sql`，索引见 `sql/02_indexes.sql`。

### 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.4.5 · JDK 21 · MyBatis-Plus 3.5.16 · MinIO SDK 8.5.17 |
| 数据库 / 缓存 | PostgreSQL 18 · Redis 8 |
| 对象存储 | MinIO（音频私有桶 + 封面公开桶） |
| 鉴权 | Session + Redis（`X-Token` 请求头，TTL 24h，访问即续期；**不用 JWT**） |
| 前端 | Vue 3 · Vite 6 · Element Plus · Pinia · Vue Router · Tailwind CSS · ECharts · Axios |
| 包管理 | 后端 Maven · 前端 pnpm 10 |
| 部署 | Docker + Docker Compose（多阶段镜像构建） |

---

## 二、系统架构

发行版（`docker-compose.yml`）由 **5 个服务** 组成，全部容器化、一键拉起：

```
                          ┌──────────────┐
   浏览器  http://HOST:APP_PORT  │    nginx     │  前端静态产物 + SPA 路由回退
   ──────────────────────▶│  (frontend)  │
                          └──┬────────┬──┘
                /api/ (≤100m) │        │ :MINIO_PORT 直透(保留 Host)
                               ▼        ▼
                        ┌─────────┐  ┌─────────┐
                        │ backend │  │  minio  │  音频私有桶(预签名)/封面公开桶(直链)
                        │  :8080  │  │  :9000  │
                        └──┬───┬──┘  └─────────┘
                           │   │
          ┌────────────────┘   └───────────────┐
          ▼                                    ▼
     ┌──────────┐                        ┌──────────┐
     │ postgres │                        │  redis   │  排行榜 ZSET + 会话存储
     │  :5432   │                        │  :6379   │
     └──────────┘                        └──────────┘
```

**数据流要点**

- **API 请求**：浏览器 `GET/POST /api/...` → nginx `/api/` 反代 → backend:8080。
- **音乐上传**：浏览器 → nginx `/api/`（请求体上限 100m）→ backend → MinIO `music-audio` 私有桶。
- **音频播放**：backend 签发**预签名 URL**（host = `PUBLIC_HOST:MINIO_PORT`）→ 浏览器直连 nginx `:MINIO_PORT` → 透传 MinIO（原样转发 Host，使 SigV4 校验通过）。
- **封面**：MinIO `music-cover` 公开桶直链，同样经 nginx `:MINIO_PORT` 透传。

> 两个 MinIO 桶（`music-audio`、`music-cover`）由 **backend 启动时自动创建**，无需手动建桶。

---

## 三、环境要求

### 方式 A：一键 Docker 部署（推荐，生产 / 演示）

仅需：

- **Docker**（含 BuildKit）
- **Docker Compose** v2（`docker compose` 子命令）

> 容器内构建已配置国内镜像源（Maven 走阿里云、pnpm 走 npmmirror、apt 走阿里云），首次构建在国内网络下亦可正常完成。

### 方式 B：本地开发

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **21** | 系统 `JAVA_HOME` 可能指向旧版，构建/运行前务必确认 `java -version` 为 21 |
| Maven | 3.9+ | 后端编译运行 |
| Node.js | 20+ | 前端构建 |
| pnpm | 10 | 前端包管理（`corepack enable` 后 `pnpm` 即用） |
| Docker + Compose | — | 用于起 PostgreSQL / Redis / MinIO 依赖（见 `docker-compose.dev.yml`） |

---

## 四、快速部署（生产 / 演示）

> 适用 `docker-compose.yml`：5 服务整栈编排，PostgreSQL 只建 schema + 索引（**干净库，不含数据**），测试数据由脚本按需导入。

### 1. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，**对外访问必须改 `PUBLIC_HOST`**：

```dotenv
PUBLIC_HOST=192.168.0.4      # ← 改为你访问本机的 IP / 域名（仅本机访问可保持 localhost）
APP_PORT=8080                # nginx 应用端口
MINIO_PORT=9000              # nginx 直透 MinIO 的端口
POSTGRES_PASSWORD=music123        # 数据库密码
MINIO_ROOT_PASSWORD=music123456   # MinIO 密码（≥8 位）
```

> ⚠️ 若 `PUBLIC_HOST` 不对（例如保持 `localhost` 却用局域网 IP 访问），会出现**接口正常但音频播放/封面加载失败**——因为预签名 URL 指向了浏览器无法访问的地址。详见[常见问题](#七部署运维与常见问题)。

### 2. 构建 + 启动

```bash
docker compose up --build -d
```

- `--build`：首次运行或改动 `backend/`、`frontend/`、配置后须带此参数重建镜像。
- 首次构建会下载 Maven 依赖与 npm 包（已走国内镜像源），耗时数分钟；`postgres/redis/minio` 拉基础镜像后即可就绪。

### 3. 查看启动状态

```bash
docker compose ps
```

等待 `backend` 健康检查列为 `healthy`（`start_period` 约 40s，含首次依赖下载）。

健康检查（容器内）：

```bash
docker compose exec backend curl -s localhost:8080/actuator/health
# 期望：{"status":"UP","components":{...}}
```

### 4.（可选）导入测试数据

发行版默认是干净库。如需演示账号 / 歌曲 / 榜单，执行（postgres 容器须在运行）：

```bash
./scripts/seed-testdata.sh          # Linux / Git Bash
# 或 PowerShell：
./scripts/seed-testdata.ps1
```

> 脚本将 `sql/03_seed.sql` 拷进 postgres 容器并执行。种子数据**只含数据库行，不含真实媒体文件**——种子歌曲可浏览/进榜/可审核，但点播放与封面会 404；**演示播放请用 app 自行上传一首**。

### 5. 访问应用

| 入口 | 地址 |
| --- | --- |
| 应用首页 | `http://<PUBLIC_HOST>:<APP_PORT>` （默认 `http://localhost:8080`） |
| 后端健康检查 | 容器内 `/actuator/health`（见上） |

登录后即可上传歌曲、试听、收藏、评论、看排行榜。

---

## 五、本地开发

> 适用 `docker-compose.dev.yml`：只起 PostgreSQL / Redis / MinIO 三个**依赖**，后端用 Maven 本地跑、前端用 Vite dev server，支持热重载。

### 1. 起数据库 / 缓存 / 对象存储依赖

```bash
docker compose -f docker-compose.dev.yml up -d
```

- 首次启动会**自动按序执行** `sql/01_schema.sql` → `02_indexes.sql` → `03_seed.sql`（建表 + 索引 + 测试数据）。
- 端口映射：

  | 服务 | 容器 → 宿主 | 说明 |
  | --- | --- | --- |
  | PostgreSQL | `5432:5432` | 库名 `music_share`，用户 `music` |
  | Redis | `6380:6379` | 映射到 6380，避开本机已占用的 6379 |
  | MinIO S3 API | `9000:9000` | 后端 SDK 与预签名 URL |
  | MinIO 控制台 | `9001:9001` | 浏览器查看桶/对象（账号 `music` / `music123456`） |

### 2. 启动后端

> 确认 `java -version` 为 **JDK 21**（系统 `JAVA_HOME` 可能指向旧版）。

在 `backend/` 目录：

```bash
cd backend
mvn spring-boot:run
# 或先打包再运行：mvn clean package -DskipTests && java -jar target/*.jar
```

- 默认监听 `8080`，连接 `localhost:5432`（PG）、`localhost:6380`（Redis）、`localhost:9000`（MinIO）。
- 集成测试依赖 Testcontainers（会起真实容器），**不要用 `-DskipTests` 跳过集成测试时** 确保本机 Docker 可用：`mvn test`。

### 3. 启动前端

在 `frontend/` 目录：

```bash
cd frontend
corepack enable          # 启用 pnpm（Node 自带 corepack）
pnpm install
pnpm dev
```

- Vite dev server 监听 `http://localhost:5173`。
- `/api` 请求由 Vite 代理到 `http://localhost:8080`（见 `vite.config.ts`），开发期无跨域。

---

## 六、配置参考

### 发行版环境变量（`.env`）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PUBLIC_HOST` | `localhost` | **浏览器访问本系统的 host**（IP / 域名）。决定预签名 URL 与封面直链的 host，对外访问必改 |
| `APP_PORT` | `8080` | nginx 对外端口（应用首页） |
| `MINIO_PORT` | `9000` | nginx 直透 MinIO 的端口（音频/封面） |
| `POSTGRES_PASSWORD` | `music123` | PostgreSQL `music` 用户密码（同时注入 backend 数据源） |
| `MINIO_ROOT_PASSWORD` | `music123456` | MinIO root 密码（即 secret key，≥8 位） |

> `docker-compose.yml` 中各变量均带 `${VAR:-default}`，不建 `.env` 也能以默认值跑起；建了 `.env` 才会被覆盖。

### 后端关键配置（`backend/src/main/resources/application.yml`，开发态）

| 配置项 | 值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| 数据源 | `localhost:5432/music_share`，`music/music123` | 对应 dev compose |
| Redis | `localhost:6380` | 对应 dev compose 映射 |
| MinIO endpoint | `http://localhost:9000` | SDK 访问地址 |
| `storage.minio.audio-bucket` | `music-audio` | 音频私有桶（预签名 URL） |
| `storage.minio.cover-bucket` | `music-cover` | 封面公开桶（直链） |
| `storage.minio.presigned-expiry-seconds` | `7200` | 音频预签名 URL 有效期（2 小时） |
| `spring.servlet.multipart.max-file-size` | `100MB` | 单文件上限（支持无损 flac） |

> 发行版中 PG/Redis/MinIO 连接信息由 `docker-compose.yml` 的 `environment` 注入覆盖（指向容器名 `postgres` / `redis` / `minio`）。

### 端口一览

| 端口 | 协议 | 环境 | 归属 |
| --- | --- | --- | --- |
| `APP_PORT` (8080) | HTTP | 发行版 | nginx（应用首页 + `/api`） |
| `MINIO_PORT` (9000) | HTTP | 发行版 | nginx（透传 MinIO 音频/封面） |
| 5432 | TCP | 开发 | PostgreSQL |
| 6380 | TCP | 开发 | Redis |
| 9000 / 9001 | HTTP | 开发 | MinIO S3 API / 控制台 |
| 8080 | HTTP | 开发 | 后端（Maven 运行） |
| 5173 | HTTP | 开发 | 前端（Vite dev server） |

---

## 七、部署运维与常见问题

### 改了配置 / 代码后如何生效？

- 改 `.env`：`docker compose up -d`（env 运行时注入，**仅 recreate 受影响服务**，无需重建镜像）。
- 改 `backend/` 或 `frontend/` 源码 / `application.yml` / `nginx.conf` / `Dockerfile`：**必须** `docker compose up --build -d` 重建对应镜像（yml 打进 jar、nginx.conf 经 COPY 入镜像）。

### 常用运维命令

```bash
docker compose logs -f backend        # 跟随后端日志
docker compose logs -f nginx          # 跟随 nginx 日志
docker compose ps                     # 查看服务与健康状态
docker compose down                   # 停止并移除容器（数据卷保留）
docker compose down -v                # 连同数据卷一起清除（⚠️ 库/对象全清，慎用）
```

### FAQ

**Q1：接口能通，但音频播放不了 / 封面加载失败（404）。**
A：`PUBLIC_HOST` 没设对。预签名 URL 与封面直链的 host 取自 `PUBLIC_HOST`，必须等于浏览器实际访问的 host。例如用局域网 IP `192.168.0.4` 访问，则 `.env` 里 `PUBLIC_HOST=192.168.0.4`，然后 `docker compose up -d`（backend 会 recreate）。

**Q2：上传音乐报 `413 Request Entity Too Large`。**
A：上传链路为 `浏览器 → nginx /api/ (100m) → backend (100MB) → MinIO`。当前 nginx 与 backend 均已放宽至 100MB，支持无损 flac。若仍 413，检查是否改过 `nginx.conf` 的 `client_max_body_size` 或 `application.yml` 的 multipart 上限——**nginx 值须 ≥ backend 值**，否则请求到不了后端。

**Q3：本地构建后端报 JDK 版本错 / 编译失败。**
A：确认 `java -version` 与 `JAVA_HOME` 均指向 **JDK 21**（系统常残留旧版）。容器内构建不受影响（Dockerfile 用 `maven:3.9-eclipse-temurin-21`）。

**Q4：首次 `docker compose up --build` 很慢。**
A：首次需下载基础镜像 + Maven/npm 依赖。容器内已配置国内镜像源（阿里云 / npmmirror）加速；拉基础镜像若仍慢，可在 Docker daemon 配置 registry-mirror。

**Q5：发行版想看 MinIO 控制台。**
A：发行版 `nginx` 仅透传 `:9000`（S3 API），**未暴露控制台 :9001**。如需控制台，可在 `docker-compose.yml` 的 `minio` 服务下补 `ports: ["9001:9001"]` 后 `docker compose up -d`。

---

## 八、目录结构

```
.
├── backend/                     后端 Spring Boot（Java 21）
│   ├── src/main/java/com/music/        按模块分包（common / user / song / album …）
│   │   └── common/                     公共基础设施（统一返回体 / 全局异常 / 鉴权拦截器 / MinIO 配置）
│   ├── src/main/resources/application.yml
│   ├── Dockerfile               多阶段：Maven 构建 → JRE21 运行
│   ├── settings.xml             容器内阿里云 Maven 镜像
│   └── pom.xml
├── frontend/                    前端 Vue3 + Vite
│   ├── src/
│   ├── Dockerfile               多阶段：pnpm 构建 → nginx 托管
│   ├── nginx.conf               /api 反代 + MinIO :9000 直透
│   └── package.json
├── sql/                         数据库脚本
│   ├── 01_schema.sql            建表（10 张）
│   ├── 02_indexes.sql           索引
│   └── 03_seed.sql              测试数据（账号 / 歌曲 / 榜单）
├── scripts/                     测试数据导入脚本（发行版用）
│   ├── seed-testdata.sh         Linux / Git Bash
│   └── seed-testdata.ps1        PowerShell
├── docker-compose.yml           ★ 发行版 5 服务编排（生产 / 演示）
├── docker-compose.dev.yml       本地开发依赖（PG / Redis / MinIO）
├── .env.example                 发行版可调参数模板
└── README.md                    本文件
```

> `docs/`（设计文档、接口文档、开发日志）与 `CLAUDE.md` 为本地协作资料，不随仓库分发。

---

## 九、默认账号与演示说明

导入测试数据后（开发态自动执行 `03_seed.sql`；发行态运行 `scripts/seed-testdata.*`），可用以下账号登录。

**所有测试账号的明文密码均为 `123456`**（库内存 bcrypt 密文）。

| 用户名 | 角色 | 备注 |
| --- | --- | --- |
| `admin` | 管理员 | 歌曲/用户管理、审核 |
| `uploader_jay` / `uploader_eason` / `uploader_tyler` | 上传者 | 可上传歌曲 |
| `alice` / `bob` / `carol` / `david` | 普通用户 | 播放/收藏/评论 |
| `banned_user` | 已封禁 | 用于演示封禁即时失效（登录即拒） |

### 演示建议

1. 用 `admin` 登录 → 后台审核一首待审歌曲（通过）。
2. 用 `uploader_jay` 登录 → **上传一首本地音乐**（演示真实播放；种子歌曲无媒体文件无法播放）。
3. 上传后回首页播放 → 验证音频/封面正常、加入收藏 / 歌单 / 评论 / 评分。
4. 查看「排行榜」（基于播放量的 Redis ZSET）。
5. 用 `banned_user` 登录 → 验证封禁账号被拒（Session 即时作废）。
