package com.music.file.dto;

import java.util.List;

/**
 * 孤儿扫描清理结果。
 *
 * <p>按桶分别统计：扫描到的对象数、判定为孤儿的数量、实际删除数；
 * dryRun 模式下 deleted 恒为 0，orphanKeys 列出将被删的 key 供核对。</p>
 */
public class OrphanScanResultVO {

    /** 是否为试运行(只扫不删)。 */
    private boolean dryRun;

    /** 安全期(分钟)：早于此期限的无引用对象才视为孤儿。 */
    private int safeMinutes;

    /** 音频桶扫描数。 */
    private int audioScanned;

    /** 封面桶扫描数。 */
    private int coverScanned;

    /** 判定为孤儿的总数。 */
    private int orphanCount;

    /** 实际删除数(dryRun 时为 0)。 */
    private int deletedCount;

    /** 孤儿 key 列表(含桶前缀标识，便于核对)。 */
    private List<String> orphanKeys;

    public OrphanScanResultVO() {
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int getSafeMinutes() {
        return safeMinutes;
    }

    public void setSafeMinutes(int safeMinutes) {
        this.safeMinutes = safeMinutes;
    }

    public int getAudioScanned() {
        return audioScanned;
    }

    public void setAudioScanned(int audioScanned) {
        this.audioScanned = audioScanned;
    }

    public int getCoverScanned() {
        return coverScanned;
    }

    public void setCoverScanned(int coverScanned) {
        this.coverScanned = coverScanned;
    }

    public int getOrphanCount() {
        return orphanCount;
    }

    public void setOrphanCount(int orphanCount) {
        this.orphanCount = orphanCount;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public List<String> getOrphanKeys() {
        return orphanKeys;
    }

    public void setOrphanKeys(List<String> orphanKeys) {
        this.orphanKeys = orphanKeys;
    }
}
