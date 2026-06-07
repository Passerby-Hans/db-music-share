package com.music.file.dto;

/**
 * 文件上传响应数据。
 *
 * <p>音频返回 {@code key}(落库到歌曲 audioPath，私有不给直链)；
 * 封面额外返回 {@code url}(公开桶直链，便于前端即时预览)。</p>
 */
public class UploadResultVO {

    /** object key(业务落库用)。 */
    private String key;

    /** 公开直链(仅封面有；音频为 null)。 */
    private String url;

    public UploadResultVO() {
    }

    public UploadResultVO(String key, String url) {
        this.key = key;
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
