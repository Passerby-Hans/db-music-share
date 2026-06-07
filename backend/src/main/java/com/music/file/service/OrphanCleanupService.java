package com.music.file.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.music.common.storage.StorageProperties;
import com.music.common.storage.StorageService;
import com.music.common.storage.StorageService.BucketType;
import com.music.common.storage.StorageService.ObjectInfo;
import com.music.file.dto.OrphanScanResultVO;
import com.music.song.entity.Album;
import com.music.song.entity.Song;
import com.music.song.mapper.AlbumMapper;
import com.music.song.mapper.SongMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 孤儿文件扫描清理服务。
 *
 * <p>孤儿 = MinIO 中存在、但不被任何 DB 记录引用的对象。引用集取自全部歌曲的
 * {@code audioPath}/{@code cover} 与全部专辑的 {@code cover}，<b>无视软删</b>
 * （软删记录虽逻辑删除，但本项目删除即同步物理删文件，残留引用按"仍引用"处理，宁可不误删）。</p>
 *
 * <p>仅删除 <b>早于安全期</b>（默认 2h）的无引用对象，避开"上传拿到 key 但尚未建歌"的竞态。
 * 极简方案不依赖临时登记表，靠"无引用 + 够旧"两条件兜底。</p>
 */
@Service
public class OrphanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OrphanCleanupService.class);

    private final StorageService storageService;
    private final StorageProperties props;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;

    public OrphanCleanupService(StorageService storageService, StorageProperties props,
                                SongMapper songMapper, AlbumMapper albumMapper) {
        this.storageService = storageService;
        this.props = props;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
    }
    /**
     * 执行孤儿扫描(可选清理)。
     *
     * @param dryRun true 只扫描统计不删除；false 删除判定的孤儿
     * @return 扫描/清理结果
     */
    public OrphanScanResultVO scan(boolean dryRun) {
        // 1. 收集 DB 全量引用 key(歌曲 audioPath/cover + 专辑 cover，无视软删)
        Set<String> audioRefs = new HashSet<>();
        Set<String> coverRefs = new HashSet<>();
        for (Song s : songMapper.selectList(Wrappers.emptyWrapper())) {
            addIfPresent(audioRefs, s.getAudioPath());
            addIfPresent(coverRefs, s.getCover());
        }
        for (Album a : albumMapper.selectList(Wrappers.emptyWrapper())) {
            addIfPresent(coverRefs, a.getCover());
        }

        // 2. 安全期阈值：早于此刻的无引用对象才删
        Instant threshold = Instant.now().minus(props.getOrphanSafeMinutes(), ChronoUnit.MINUTES);

        OrphanScanResultVO result = new OrphanScanResultVO();
        result.setDryRun(dryRun);
        result.setSafeMinutes(props.getOrphanSafeMinutes());
        List<String> orphanKeys = new ArrayList<>();

        // 3. 逐桶比对
        int deleted = 0;
        List<ObjectInfo> audioObjs = storageService.listObjects(BucketType.AUDIO);
        result.setAudioScanned(audioObjs.size());
        deleted += sweep(BucketType.AUDIO, audioObjs, audioRefs, threshold, dryRun, orphanKeys);

        List<ObjectInfo> coverObjs = storageService.listObjects(BucketType.COVER);
        result.setCoverScanned(coverObjs.size());
        deleted += sweep(BucketType.COVER, coverObjs, coverRefs, threshold, dryRun, orphanKeys);

        result.setOrphanCount(orphanKeys.size());
        result.setDeletedCount(deleted);
        result.setOrphanKeys(orphanKeys);
        log.info("孤儿扫描完成 dryRun={} 安全期={}min 扫描audio={}/cover={} 孤儿={} 删除={}",
                dryRun, props.getOrphanSafeMinutes(), audioObjs.size(), coverObjs.size(),
                orphanKeys.size(), deleted);
        return result;
    }

    /**
     * 扫一个桶：把"无引用且够旧"的对象计为孤儿，非 dryRun 则删除。
     *
     * @param bucket     桶类型
     * @param objects    桶内对象
     * @param refs       该桶对应的引用 key 集
     * @param threshold  安全期阈值(早于它才删)
     * @param dryRun     是否只扫不删
     * @param orphanKeys 收集孤儿 key(加桶前缀标识)
     * @return 实际删除数
     */
    private int sweep(BucketType bucket, List<ObjectInfo> objects, Set<String> refs,
                      Instant threshold, boolean dryRun, List<String> orphanKeys) {
        int deleted = 0;
        for (ObjectInfo obj : objects) {
            if (refs.contains(obj.key())) {
                continue;  // 被引用，保留
            }
            if (obj.lastModified().isAfter(threshold)) {
                continue;  // 太新，可能正等待建歌引用，跳过
            }
            orphanKeys.add(bucket + ":" + obj.key());
            if (!dryRun) {
                storageService.delete(bucket, obj.key());
                deleted++;
            }
        }
        return deleted;
    }

    /** 非空 key 加入集合。 */
    private void addIfPresent(Set<String> set, String key) {
        if (key != null && !key.isBlank()) {
            set.add(key);
        }
    }
}
