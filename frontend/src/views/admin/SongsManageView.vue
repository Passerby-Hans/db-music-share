<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAllSongs } from '@/api/admin'
import { deleteSong } from '@/api/song'
import { AuditStatus, type SongVO } from '@/api/types'

/**
 * 歌曲管理页（管理后台 role=2）：全站歌曲（含各审核态 + 软删），筛选 + 下架（软删）。
 * 下架复用 DELETE /api/song/{sid}（管理员越权软删 + 物理删音频/封面文件）。
 */
const songs = ref<SongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

/** 筛选：标题关键字 + 审核态（''=全部）。 */
const keyword = ref('')
const auditStatus = ref<number | ''>('')

/** 审核态下拉选项。 */
const statusOptions: { label: string; value: number | '' }[] = [
  { label: '全部', value: '' },
  { label: '待审', value: AuditStatus.PENDING },
  { label: '通过', value: AuditStatus.PASSED },
  { label: '驳回', value: AuditStatus.REJECTED },
]

/** 审核态 → el-tag 类型/文本。 */
function statusMeta(
  s: number,
): { text: string; type: 'success' | 'warning' | 'danger' | 'info' } {
  if (s === AuditStatus.PASSED) return { text: '已通过', type: 'success' }
  if (s === AuditStatus.REJECTED) return { text: '已驳回', type: 'danger' }
  return { text: '待审', type: 'warning' }
}

async function load() {
  loading.value = true
  try {
    const res = await listAllSongs({
      keyword: keyword.value || undefined,
      auditStatus: auditStatus.value === '' ? undefined : auditStatus.value,
      page: page.value,
      size: size.value,
    })
    songs.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSearch() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

const acting = reactive<Record<number, boolean>>({})

/** 下架（软删）一首歌，二次确认。 */
async function takeDown(s: SongVO) {
  await ElMessageBox.confirm(
    `确定下架《${s.title}》？下架即软删并清理其音频/封面文件。`,
    '下架确认',
    { type: 'warning' },
  )
  acting[s.sid] = true
  try {
    await deleteSong(s.sid)
    ElMessage.success(`《${s.title}》已下架`)
    await load()
  } finally {
    acting[s.sid] = false
  }
}
</script>

<template>
  <div class="songs-mgmt app-page-wide">
    <section class="page-hero p-6 mb-6">
      <span class="hero-kicker">Content</span>
      <h1 class="hero-title mt-4" style="font-size: clamp(1.5rem, 3vw, 2.4rem)">歌曲管理</h1>
      <p class="hero-subtitle mt-2">全站歌曲：浏览、按审核态筛选、下架违规内容。</p>
    </section>

    <el-card>
      <template #header>
        <div class="head">
          <el-input
            v-model="keyword"
            placeholder="搜索标题"
            clearable
            class="search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select v-model="auditStatus" class="filter" @change="onSearch">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button :icon="'Refresh'" @click="load">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && songs.length === 0" description="暂无歌曲" />

      <el-table v-else v-loading="loading" :data="songs">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="uploaderUid" label="上传者 uid" width="110" />
        <el-table-column label="审核态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.auditStatus).type" size="small">
              {{ statusMeta(row.auditStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="playCount" label="播放量" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isDeleted ? 'info' : 'success'" size="small">
              {{ row.isDeleted ? '已下架' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              size="small"
              type="danger"
              :disabled="row.isDeleted === true"
              :loading="acting[row.sid]"
              @click="takeDown(row)"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="songs.length" class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search {
  max-width: 240px;
}
.filter {
  width: 130px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
