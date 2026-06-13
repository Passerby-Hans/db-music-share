<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orphanScan } from '@/api/admin'
import type { OrphanScanVO } from '@/api/types'

/**
 * 存储维护页：孤儿文件扫描/清理。
 * 流程：先「演练扫描」(dryRun=true) 看清单 → 确认无误再「执行清理」(dryRun=false) 删除。
 * 孤儿 = MinIO 中存在但无任何 DB 引用、且超过安全期的对象。
 */
const result = ref<OrphanScanVO | null>(null)
const scanning = ref(false)
const cleaning = ref(false)

/** 演练扫描：只列清单不删。 */
async function dryScan() {
  scanning.value = true
  try {
    result.value = await orphanScan(true)
    ElMessage.success(`扫描完成，发现 ${result.value.orphanCount} 个孤儿对象`)
  } finally {
    scanning.value = false
  }
}

/** 执行清理：真实删除（二次确认）。 */
async function doClean() {
  await ElMessageBox.confirm(
    '将永久删除所有「无引用且超过安全期」的孤儿对象，不可恢复。确定执行？',
    '清理确认',
    { type: 'warning' },
  )
  cleaning.value = true
  try {
    result.value = await orphanScan(false)
    ElMessage.success(`清理完成，删除 ${result.value.deletedCount} 个对象`)
  } finally {
    cleaning.value = false
  }
}
</script>

<template>
  <div class="storage">
    <el-card>
      <template #header><span>存储维护 · 孤儿文件清理</span></template>

      <el-alert type="info" :closable="false" show-icon class="intro">
        孤儿文件 = MinIO 中存在、但不被任何歌曲/专辑引用、且超过安全期（默认 120 分钟）的对象。
        建议先「演练扫描」查看清单，确认无误后再「执行清理」。系统每天 04:30 也会自动清理一次。
      </el-alert>

      <div class="actions">
        <el-button type="primary" :loading="scanning" @click="dryScan">演练扫描</el-button>
        <el-button type="danger" :loading="cleaning" :disabled="!result" @click="doClean">
          执行清理
        </el-button>
      </div>

      <template v-if="result">
        <el-descriptions :column="3" border class="stat">
          <el-descriptions-item label="模式">
            {{ result.dryRun ? '演练（未删除）' : '已执行删除' }}
          </el-descriptions-item>
          <el-descriptions-item label="安全期">{{ result.safeMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="孤儿数">{{ result.orphanCount }}</el-descriptions-item>
          <el-descriptions-item label="音频扫描">{{ result.audioScanned }}</el-descriptions-item>
          <el-descriptions-item label="封面扫描">{{ result.coverScanned }}</el-descriptions-item>
          <el-descriptions-item label="已删除">{{ result.deletedCount }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="result.orphanKeys.length" class="keys">
          <div class="keys-title">孤儿清单：</div>
          <el-scrollbar max-height="240px">
            <div v-for="k in result.orphanKeys" :key="k" class="key-item">{{ k }}</div>
          </el-scrollbar>
        </div>
        <el-empty v-else description="无孤儿对象" :image-size="80" />
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.storage {
  max-width: 900px;
  margin: 0 auto;
}
.intro {
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.stat {
  margin-bottom: 16px;
}
.keys-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.key-item {
  font-family: monospace;
  font-size: 13px;
  padding: 4px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  word-break: break-all;
}
</style>
