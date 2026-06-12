package com.music.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * 对象存储抽象。
 *
 * <p>把"文件存哪、怎么取、怎么删"与业务解耦：业务只持有返回的 object key，
 * 日后换存储后端(本地盘/OSS/S3)只需另写实现，业务代码不动。
 * 当前实现见 {@link MinioStorageService}(MinIO)。</p>
 */
public interface StorageService {

    /** 存储桶类型：音频(私有) / 封面(公开) / 头像(公开，逻辑独立前缀)。 */
    enum BucketType {
        /** 音频私有桶，经预签名 URL 访问。 */
        AUDIO,
        /** 封面公开桶，直链访问。 */
        COVER,
        /**
         * 用户头像。物理上复用封面公开桶，但使用独立的 {@code avatar/} key 前缀，
         * 与歌曲封面逻辑隔离——即便出现 key 注入也波及不到封面对象。
         */
        AVATAR
    }

    /**
     * 桶内对象的精简元信息(供孤儿扫描)。
     *
     * @param key          object key
     * @param lastModified 最后修改时间
     */
    record ObjectInfo(String key, Instant lastModified) {
    }

    /**
     * 上传文件到指定桶，返回 object key。
     *
     * <p>key 由实现按 {@code 前缀/日期/UUID.ext} 规则生成，避免冲突与路径穿越；
     * 业务把该 key 落库到歌曲 {@code audioPath} 或封面字段。</p>
     *
     * @param bucket 目标桶类型
     * @param file   上传的文件
     * @return 生成的 object key
     */
    String upload(BucketType bucket, MultipartFile file);

    /**
     * 为私有桶对象生成限时预签名下载 URL(用于音频播放)。
     *
     * @param bucket 桶类型
     * @param key    object key
     * @return 限时可访问的 URL
     */
    String presignedGetUrl(BucketType bucket, String key);

    /**
     * 拼接公开桶对象的直链 URL(用于封面展示)。
     *
     * @param key object key
     * @return 公开直链；key 为空时返回 null
     */
    String publicUrl(String key);

    /**
     * 删除对象。key 为空时静默忽略；对象不存在不报错(幂等)。
     *
     * @param bucket 桶类型
     * @param key    object key
     */
    void delete(BucketType bucket, String key);

    /**
     * 枚举指定桶内全部对象(递归)，用于孤儿扫描。
     *
     * @param bucket 桶类型
     * @return 对象元信息列表
     */
    List<ObjectInfo> listObjects(BucketType bucket);
}
