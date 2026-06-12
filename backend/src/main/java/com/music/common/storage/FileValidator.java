package com.music.common.storage;

import com.music.common.exception.BizException;
import com.music.common.result.ResultCode;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Set;

/**
 * 上传文件的统一校验工具。
 *
 * <p>把「非空 / 不超限 / 扩展名白名单」三项校验集中于此，供
 * {@code FileController}（歌曲音频/封面上传）与用户头像上传等多处复用，
 * 避免各 Controller 各写一份导致口径漂移。</p>
 *
 * <p>纯静态工具，不持有状态；校验失败统一抛 {@link BizException}（400）。</p>
 */
public final class FileValidator {

    /** 音频体积上限(50MB)，与 application.yml 的 multipart 上限呼应。 */
    public static final long MAX_AUDIO_SIZE = 50L * 1024 * 1024;

    /** 图片体积上限(5MB)，封面与头像共用。 */
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 音频允许的扩展名白名单。 */
    public static final Set<String> AUDIO_EXT = Set.of("mp3", "wav", "flac", "m4a", "aac", "ogg");

    /** 图片允许的扩展名白名单（宽集合，含 webp）。 */
    public static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /**
     * 可被 {@link #validateImageContent} 内容校验可靠解码的图片扩展名集合（jpg/jpeg/png/gif）。
     * 不含 webp（部分 JDK 无 ImageIO 解码器，会导致合法 webp 误拒）。头像与封面共用——
     * 凡需「扩展名 + ImageIO 内容双校验」的图片上传都用本集合，保证内容校验不误伤白名单内格式。
     */
    public static final Set<String> DECODABLE_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif");

    private FileValidator() {
    }

    /**
     * 统一校验：非空、不超限、扩展名在白名单内。
     *
     * @param file       上传文件
     * @param maxSize    体积上限(字节)
     * @param allowedExt 允许的扩展名集合(小写无点)
     * @param label      文件类别中文名(用于错误提示，如"音频""封面""头像")
     * @throws BizException 任一校验不通过(400)
     */
    public static void validate(MultipartFile file, long maxSize, Set<String> allowedExt, String label) {
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

    /**
     * 校验文件确为可解码的真实图片（防「伪装成 .jpg 的 HTML/脚本」绕过扩展名白名单）。
     *
     * <p>用 {@link ImageIO#read} 尝试解码：能解出 {@link BufferedImage} 才视为有效图片，
     * 否则拒绝。仅查扩展名/MIME 不足以拦住主动内容——攻击者可上传 Content-Type 伪造的
     * HTML 命名为 .jpg 经公开桶直链获得可渲染入口；本校验从内容层把关。</p>
     *
     * @param file 上传文件（应先经 {@link #validate} 通过扩展名/大小校验）
     * @throws BizException 无法解码为图片（400）
     */
    public static void validateImageContent(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "文件内容不是有效图片");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片解析失败，请上传有效图片");
        }
    }
}
