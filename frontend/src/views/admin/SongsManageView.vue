<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAllSongs, auditSong } from '@/api/admin'
import { deleteSong } from '@/api/song'
import { AuditStatus, type SongVO } from '@/api/types'

/**
 * 歌曲管理页（管理后台 role=2）：全站歌曲（含各审核态 + 软删），筛选 + 审核（通过/驳回）+ 下架（软删）。
 * 合并自原「歌曲审核」页：待审歌曲可通过/驳回（auditSong）；任意歌可下架（deleteSong：管理员越权软删 + 物理删音频/封面文件）。
 */
const songs = ref<SongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

/** 筛选：标题关键字 + 审核态（''=全部）；默认「待审」= 进页即审核队列。 */
const keyword = ref('')
const auditStatus = ref<number | ''>(AuditStatus.PENDING)

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

/** 通过待审歌曲。 */
async function pass(s: SongVO) {
  acting[s.sid] = true
  try {
    await auditSong(s.sid, { pass: true })
    ElMessage.success(`《${s.title}》已通过`)
    await load()
  } finally {
    acting[s.sid] = false
  }
}

/** 驳回待审歌曲：弹窗填理由（非空）。 */
async function reject(s: SongVO) {
  const { value } = await ElMessageBox.prompt('请输入驳回理由', `驳回《${s.title}》`, {
    inputPattern: /\S+/,
    inputErrorMessage: '驳回理由不能为空',
    inputType: 'textarea',
  })
  acting[s.sid] = true
  try {
    await auditSong(s.sid, { pass: false, remark: value.trim() })
    ElMessage.success('已驳回')
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
      <p class="hero-subtitle mt-2">全站歌曲：审核（通过/驳回）、按状态筛选、下架违规内容。</p>
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
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button
              v-if="row.auditStatus === AuditStatus.PENDING && !row.isDeleted"
              size="small"
              type="success"
              :loading="acting[row.sid]"
              @click="pass(row)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.auditStatus === AuditStatus.PENDING && !row.isDeleted"
              size="small"
              type="warning"
              :loading="acting[row.sid]"
              @click="reject(row)"
            >
              驳回
            </el-button>
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
