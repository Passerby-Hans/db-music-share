package com.music.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

/**
 * 集成测试基类：以真实容器（PostgreSQL + Redis + MinIO）为后端依赖，
 * 在完整 Spring 上下文中通过 {@link MockMvc} 黑盒打 HTTP 接口。
 *
 * <p><b>单例容器模式</b>：三个容器声明为 static 字段并在静态块中手动 {@code start()}，
 * 整个 JVM 测试期间只启动一次、被所有继承本类的测试类共享（Spring 上下文亦被缓存复用）。
 * 不使用 {@code @Testcontainers}/{@code @Container} 的「每类启停」生命周期，
 * 容器随 JVM 退出由 Testcontainers 的 Ryuk 边车统一回收。</p>
 *
 * <p><b>真实数据源</b>：PostgreSQL 容器启动时按文件名顺序执行项目既有的
 * {@code sql/01_schema.sql → 02_indexes.sql → 03_seed.sql}（与 docker-compose.dev.yml
 * 同一套脚本，单一数据源、不复制），因此测试跑在真实表结构与种子数据上。</p>
 *
 * <p><b>数据隔离</b>：本类标注 {@link Transactional}，每个测试方法结束后自动回滚，
 * 增删改不会污染种子数据，测试可无限次重复执行。</p>
 *
 * <p><b>连接注入</b>：三容器的地址/端口/凭据由 {@link DynamicPropertySource} 动态写入
 * Spring 环境，覆盖 application.yml 中指向本地固定端口的配置。MinIO 桶由应用自身的
 * {@code MinioConfig} 启动初始化器自动创建，测试侧无需干预。</p>
 *
 * <p>新增模块的测试类继承本类即可复用同一套容器与 MockMvc（累积扩展，不重复造环境）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    /** PostgreSQL 镜像，与 docker-compose.dev.yml 保持一致。 */
    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    /** Redis 镜像，与 docker-compose.dev.yml 保持一致。 */
    private static final String REDIS_IMAGE = "redis:8-alpine";

    /** MinIO 镜像，与 docker-compose.dev.yml 保持一致（pin 稳定版，保证可复现）。 */
    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2025-09-07T16-13-09Z";

    /** MinIO 根账号（与生产配置一致，仅用于测试容器）。 */
    private static final String MINIO_USER = "music";

    /** MinIO 根密码（须 ≥8 位，否则 MinIO 拒绝启动）。 */
    private static final String MINIO_PASSWORD = "music123456";

    /** PostgreSQL 单例容器：挂载三段 SQL 脚本到初始化目录，按序建表/索引/灌种子。 */
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName("music_share")
                    .withUsername("music")
                    .withPassword("music123")
                    // 项目根的真实脚本（测试工作目录为 backend/，故用 ../sql 相对路径）
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../sql/01_schema.sql"),
                            "/docker-entrypoint-initdb.d/01_schema.sql")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../sql/02_indexes.sql"),
                            "/docker-entrypoint-initdb.d/02_indexes.sql")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../sql/03_seed.sql"),
                            "/docker-entrypoint-initdb.d/03_seed.sql");

    /** Redis 单例容器：暴露默认端口，供会话存储/鉴权使用。 */
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(6379);

    /** MinIO 单例容器：S3 API 在 9000 端口，等待健康检查就绪。 */
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                    .withEnv("MINIO_ROOT_USER", MINIO_USER)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready")
                            .forPort(9000)
                            .withStartupTimeout(Duration.ofSeconds(60)));

    static {
        // 单例启动：三容器并行启动以缩短首次准备时间，JVM 退出由 Ryuk 回收
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
    }

    /**
     * 把容器的实际地址/端口/凭据注入 Spring 环境，覆盖 application.yml 的固定配置。
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // 数据源（PostgreSQL 容器自带 JDBC 信息）
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // MinIO（对象存储）
        registry.add("storage.minio.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("storage.minio.access-key", () -> MINIO_USER);
        registry.add("storage.minio.secret-key", () -> MINIO_PASSWORD);
        // 公开直链基址也指向测试容器，保证 publicUrl 拼接出的地址可用
        registry.add("storage.minio.public-base-url",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }

    /** 黑盒打接口的入口；子类直接使用。 */
    @Autowired
    protected MockMvc mockMvc;
}
