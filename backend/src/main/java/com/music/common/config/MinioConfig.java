package com.music.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import com.music.common.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置与启动初始化。
 *
 * <p>提供 {@link MinioClient} Bean，并在启动时确保两个桶存在：
 * 音频私有桶、封面公开桶(对封面桶下发匿名只读策略，使直链可访问)。</p>
 */
@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    /**
     * 构建 MinIO 客户端 Bean。
     *
     * @param props 存储配置
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient(StorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }
    /**
     * 启动初始化：确保音频/封面两桶存在，并给封面桶设匿名只读策略(直链可访问)。
     *
     * @param client MinIO 客户端
     * @param props  存储配置
     * @return 启动期执行的 ApplicationRunner
     */
    @Bean
    public org.springframework.boot.ApplicationRunner minioBucketInitializer(
            MinioClient client, StorageProperties props) {
        return args -> {
            ensureBucket(client, props.getAudioBucket());
            ensureBucket(client, props.getCoverBucket());
            // 封面桶下发匿名只读策略，使 publicUrl 直链无需鉴权即可读
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(props.getCoverBucket())
                    .config(publicReadPolicy(props.getCoverBucket()))
                    .build());
            log.info("MinIO 桶就绪: audio='{}'(私有), cover='{}'(公开只读)",
                    props.getAudioBucket(), props.getCoverBucket());
        };
    }

    /**
     * 桶不存在则创建。
     *
     * @param client MinIO 客户端
     * @param bucket 桶名
     * @throws Exception MinIO 调用异常
     */
    private void ensureBucket(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO 创建桶: {}", bucket);
        }
    }

    /**
     * 生成"匿名可 GetObject"的桶策略 JSON(供封面公开直链)。
     *
     * @param bucket 桶名
     * @return 策略 JSON 字符串
     */
    private String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }
}
