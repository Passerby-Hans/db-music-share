package com.music.common.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import io.minio.http.Method;
import com.music.common.exception.BizException;
import com.music.common.result.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的 {@link StorageService} 实现。
 *
 * <p>音频入私有桶(预签名 URL 访问)、封面入公开桶(直链)。object key 采用
 * {@code 前缀/yyyy/MM/UUID.ext} 结构，按月分目录、UUID 防冲突。</p>
 */
@Service
public class MinioStorageService implements StorageService {

    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final MinioClient client;
    private final StorageProperties props;

    public MinioStorageService(MinioClient client, StorageProperties props) {
        this.client = client;
        this.props = props;
    }

    /** 按桶类型取实际桶名。 */
    private String bucketName(BucketType bucket) {
        return bucket == BucketType.AUDIO ? props.getAudioBucket() : props.getCoverBucket();
    }
    /**
     * 上传文件：生成 {@code 前缀/yyyy/MM/UUID.ext} 形式的 key 并流式写入。
     */
    @Override
    public String upload(BucketType bucket, MultipartFile file) {
        String ext = extractExtension(file.getOriginalFilename());
        String prefix = bucket == BucketType.AUDIO ? "audio" : "cover";
        String key = prefix + "/" + LocalDate.now().format(MONTH_PATH)
                + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        try (InputStream in = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucketName(bucket))
                    .object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败: " + e.getMessage());
        }
        return key;
    }

    /**
     * 生成音频私有对象的限时预签名 GET URL。
     */
    @Override
    public String presignedGetUrl(BucketType bucket, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName(bucket))
                    .object(key)
                    .expiry(props.getPresignedExpirySeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "生成播放链接失败: " + e.getMessage());
        }
    }
    /**
     * 拼接封面公开直链：{@code publicBaseUrl/coverBucket/key}。
     */
    @Override
    public String publicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return props.getPublicBaseUrl() + "/" + props.getCoverBucket() + "/" + key;
    }

    /**
     * 删除对象(幂等)：key 为空忽略；底层不存在不抛业务异常。
     */
    @Override
    public void delete(BucketType bucket, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName(bucket))
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 递归枚举桶内全部对象，收集 key 与最后修改时间。
     */
    @Override
    public List<ObjectInfo> listObjects(BucketType bucket) {
        List<ObjectInfo> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName(bucket))
                    .recursive(true)
                    .build());
            for (Result<Item> r : results) {
                Item item = r.get();
                if (!item.isDir()) {
                    objects.add(new ObjectInfo(item.objectName(), item.lastModified().toInstant()));
                }
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "枚举对象失败: " + e.getMessage());
        }
        return objects;
    }

    /**
     * 从原始文件名提取小写扩展名(含点)，无扩展名返回空串。
     *
     * @param filename 原始文件名
     * @return 形如 ".mp3" 的扩展名，或 ""
     */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase();
    }
}
