package com.music.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储(MinIO)配置项，绑定 {@code storage.minio.*}。
 *
 * <p>集中承载连接信息、桶名与预签名时效，供 {@link MinioStorageService}
 * 与 {@code MinioConfig} 读取。</p>
 */
@Component
@ConfigurationProperties(prefix = "storage.minio")
public class StorageProperties {

    /** MinIO S3 API 地址(后端 SDK 连接用)。 */
    private String endpoint;

    /** 访问密钥(即 MinIO 根用户名)。 */
    private String accessKey;

    /** 秘密密钥(即 MinIO 根密码)。 */
    private String secretKey;

    /** 音频私有桶名(经预签名 URL 访问)。 */
    private String audioBucket;

    /** 封面公开桶名(直链访问)。 */
    private String coverBucket;

    /** 音频预签名 URL 有效期(秒)。 */
    private int presignedExpirySeconds;

    /** 封面直链对外基址(浏览器可达的 MinIO 地址)。 */
    private String publicBaseUrl;

    /** 孤儿扫描安全期(分钟)：早于"当前时间-该值"且无引用的对象才删，避开上传未建歌的竞态。 */
    private int orphanSafeMinutes = 120;

    /** 孤儿清理定时任务 cron 表达式(默认每天 04:30)。 */
    private String orphanCron = "0 30 4 * * *";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getAudioBucket() {
        return audioBucket;
    }

    public void setAudioBucket(String audioBucket) {
        this.audioBucket = audioBucket;
    }

    public String getCoverBucket() {
        return coverBucket;
    }

    public void setCoverBucket(String coverBucket) {
        this.coverBucket = coverBucket;
    }

    public int getPresignedExpirySeconds() {
        return presignedExpirySeconds;
    }

    public void setPresignedExpirySeconds(int presignedExpirySeconds) {
        this.presignedExpirySeconds = presignedExpirySeconds;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getOrphanSafeMinutes() {
        return orphanSafeMinutes;
    }

    public void setOrphanSafeMinutes(int orphanSafeMinutes) {
        this.orphanSafeMinutes = orphanSafeMinutes;
    }

    public String getOrphanCron() {
        return orphanCron;
    }

    public void setOrphanCron(String orphanCron) {
        this.orphanCron = orphanCron;
    }
}
