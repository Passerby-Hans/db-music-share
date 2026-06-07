package com.music.file.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.exception.BizException;
import com.music.common.result.Result;
import com.music.common.result.ResultCode;
import com.music.common.storage.StorageService;
import com.music.common.storage.StorageService.BucketType;
import com.music.file.dto.UploadResultVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件上传接口。
 *
 * <p>两步上传的第一步：客户端先传文件拿 object key，再把 key 填入建歌/建专辑请求。
 * 仅上传者/管理员(role≥1)可用。校验大小与类型白名单后交 {@link StorageService}。</p>
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    /** 音频体积上限(50MB)，与 application.yml 的 multipart 上限呼应。 */
    private static final long MAX_AUDIO_SIZE = 50L * 1024 * 1024;

    /** 封面体积上限(5MB)。 */
    private static final long MAX_COVER_SIZE = 5L * 1024 * 1024;

    /** 音频允许的扩展名白名单。 */
    private static final Set<String> AUDIO_EXT = Set.of("mp3", "wav", "flac", "m4a", "aac", "ogg");

    /** 封面允许的扩展名白名单。 */
    private static final Set<String> COVER_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }
    /**
     * 上传音频到私有桶。
     *
     * @param file multipart 文件，表单字段名 {@code file}
     * @return 仅含 key(音频不给直链)
     */
    @PostMapping("/audio")
    @RequireRole({1, 2})
    public Result<UploadResultVO> uploadAudio(@RequestParam("file") MultipartFile file) {
        validate(file, MAX_AUDIO_SIZE, AUDIO_EXT, "音频");
        String key = storageService.upload(BucketType.AUDIO, file);
        return Result.success(new UploadResultVO(key, null));
    }

    /**
     * 上传封面到公开桶。
     *
     * @param file multipart 文件，表单字段名 {@code file}
     * @return 含 key 与公开直链 url
     */
    @PostMapping("/cover")
    @RequireRole({1, 2})
    public Result<UploadResultVO> uploadCover(@RequestParam("file") MultipartFile file) {
        validate(file, MAX_COVER_SIZE, COVER_EXT, "封面");
        String key = storageService.upload(BucketType.COVER, file);
        return Result.success(new UploadResultVO(key, storageService.publicUrl(key)));
    }

    /**
     * 统一校验：非空、不超限、扩展名在白名单内。
     *
     * @param file       上传文件
     * @param maxSize    体积上限(字节)
     * @param allowedExt 允许的扩展名集合(小写无点)
     * @param label      文件类别中文名(用于错误提示)
     */
    private void validate(MultipartFile file, long maxSize, Set<String> allowedExt, String label) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, label + "文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    label + "文件超过大小上限(" + (maxSize / 1024 / 1024) + "MB)");
        }
        String name = file.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String ext = (dot < 0 || dot == name.length() - 1) ? "" : name.substring(dot + 1).toLowerCase();
        if (!allowedExt.contains(ext)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "不支持的" + label + "格式，仅允许: " + String.join("/", allowedExt));
        }
    }
}
