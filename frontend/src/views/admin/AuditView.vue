<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPendingSongs, auditSong } from '@/api/admin'
import type { SongVO } from '@/api/types'

/**
 * 歌曲审核页：列待审歌曲，逐首通过或驳回（驳回需填理由）。
 * 审核通过后该歌进入口径A，广场可见、可播放——主链路在此闭环。
 */
const songs = ref<SongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    const res = await listPendingSongs(page.value, size.value)
    songs.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onPageChange(p: number) {
  page.value = p
  load()
}

const acting = reactive<Record<number, boolean>>({})

/** 通过。 */
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

/** 驳回：弹窗填理由。 */
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
  <div class="audit app-page-wide">
    <section class="page-hero p-6 mb-6">
      <span class="hero-kicker">Review</span>
      <h1 class="hero-title mt-4" style="font-size: clamp(1.5rem, 3vw, 2.4rem)">歌曲审核</h1>
      <p class="hero-subtitle mt-2">审核上传者提交的歌曲，决定公开或驳回。</p>
    </section>
    <el-card>
      <template #header>
        <div class="head">
          <span>歌曲审核（待审 {{ total }} 首）</span>
          <el-button :icon="'Refresh'" @click="load">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && songs.length === 0" description="暂无待审歌曲" />

      <el-table v-else v-loading="loading" :data="songs">
        <el-table-column label="封面" width="72">
          <template #default="{ row }">
            <el-image :src="row.cover ?? undefined" fit="cover" class="cover">
              <template #error><div class="cover-ph">🎵</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="sid" label="sid" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ fmt(row.duration) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderUid" label="上传者 uid" width="110" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" type="success" :loading="acting[row.sid]" @click="pass(row)">
              通过
            </el-button>
            <el-button size="small" type="danger" :loading="acting[row.sid]" @click="reject(row)">
              驳回
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
  justify-content: space-between;
  align-items: center;
}
.cover {
  width: 48px;
  height: 48px;
  border-radius: 4px;
}
.cover-ph {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
