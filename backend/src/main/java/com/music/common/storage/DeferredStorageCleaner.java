package com.music.common.storage;

import com.music.common.storage.StorageService.BucketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 延迟到事务提交后再执行的对象存储删除器。
 *
 * <p><b>解决的问题</b>：物理删除 MinIO 对象是<u>不可逆副作用</u>。若在数据库事务<b>提交前</b>
 * 就删文件，一旦后续语句抛异常或事务回滚，数据库恢复如初、文件却已永久消失，
 * 留下"记录存活但文件丢失"的悬空引用（孤儿清理也救不回——它只清"无引用的文件"，
 * 救不了"有引用但文件没了"）。反之若把删文件放在提交后，则提交成功才删，万一删失败
 * 也只是留下"无引用的文件"，恰好由孤儿清理兜底回收。</p>
 *
 * <p><b>行为</b>：</p>
 * <ul>
 *   <li>当前线程<b>存在活跃事务</b>时：注册 {@link TransactionSynchronization#afterCommit()} 钩子，
 *       事务成功提交后才删；若事务回滚，钩子不触发，文件得以保留。</li>
 *   <li><b>无活跃事务</b>时（如未加 {@code @Transactional} 的方法）：立即删除。</li>
 * </ul>
 *
 * <p><b>失败处理</b>：删除异常一律只记日志、不再抛出。原因有二：
 * 其一，afterCommit 阶段事务已提交，此时抛异常无法回滚且会污染调用链；
 * 其二，删不掉的文件会变成无引用对象，由 {@code OrphanCleanupTask} 定时兜底回收。
 * 故"删文件失败"不应影响主业务结果。</p>
 */
@Component
public class DeferredStorageCleaner {

    private static final Logger log = LoggerFactory.getLogger(DeferredStorageCleaner.class);

    private final StorageService storageService;

    public DeferredStorageCleaner(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 在当前事务提交后删除指定对象；无活跃事务时立即删除。
     *
     * @param bucket 目标桶类型
     * @param key    对象 key；为空时静默忽略（不注册钩子）
     */
    public void deleteAfterCommit(BucketType bucket, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        // 无活跃事务：没有"提交"可等，直接删（删失败仅记日志）
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeDelete(bucket, key);
            return;
        }
        // 有活跃事务：登记提交后回调，确保 DB 真正落定才删文件
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeDelete(bucket, key);
            }
        });
    }

    /**
     * 执行删除并吞掉异常：失败只记日志，留待孤儿清理兜底。
     *
     * @param bucket 目标桶类型
     * @param key    对象 key
     */
    private void safeDelete(BucketType bucket, String key) {
        try {
            storageService.delete(bucket, key);
        } catch (Exception e) {
            // 删失败的对象将沦为无引用孤儿，由 OrphanCleanupTask 定时回收，此处不向上抛
            log.warn("提交后删除对象失败，留待孤儿清理: bucket={} key={} err={}",
                    bucket, key, e.getMessage());
        }
    }
}
