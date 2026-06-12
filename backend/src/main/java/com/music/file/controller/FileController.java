package com.music.file.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.result.Result;
import com.music.common.storage.FileValidator;
import com.music.common.storage.StorageService;
import com.music.common.storage.StorageService.BucketType;
import com.music.file.dto.UploadResultVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。
 *
 * <p>两步上传的第一步：客户端先传文件拿 object key，再把 key 填入建歌/建专辑请求。
 * 仅上传者/管理员(role≥1)可用。校验交 {@link FileValidator}（大小+类型白名单），
 * 存储交 {@link StorageService}。</p>
 *
 * <p>注：用户头像上传是面向所有登录用户的独立场景，走 {@code POST /api/user/avatar}，
 * 不在本控制器（本控制器要求 role≥1）。</p>
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

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
        FileValidator.validate(file, FileValidator.MAX_AUDIO_SIZE, FileValidator.AUDIO_EXT, "音频");
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
        FileValidator.validate(file, FileValidator.MAX_IMAGE_SIZE, FileValidator.IMAGE_EXT, "封面");
        String key = storageService.upload(BucketType.COVER, file);
        return Result.success(new UploadResultVO(key, storageService.publicUrl(key)));
    }
}
