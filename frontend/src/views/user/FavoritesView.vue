<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyFavorites, unfavorite } from '@/api/favorite'
import { usePlayerStore } from '@/stores/player'
import { AuditStatus, type FavoriteSongVO, type SongVO } from '@/api/types'

/**
 * 我的收藏：按收藏时间倒序列出。失效歌（playable=false）置灰、拦截播放，
 * 但仍展示（保留「收藏过什么」的完整记录）。可取消收藏、可进歌曲详情。
 */
const player = usePlayerStore()
const router = useRouter()

const list = ref<FavoriteSongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)

function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    const res = await listMyFavorites(page.value, size.value)
    list.value = res.records
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

/** 把收藏项转为播放器需要的 SongVO（playable 收藏必为已审核）。 */
function toSongVO(s: FavoriteSongVO): SongVO {
  return {
    sid: s.sid,
    title: s.title,
    cover: s.cover,
    duration: s.duration,
    playCount: s.playCount,
    albumAid: s.albumAid,
    uploaderUid: s.uploaderUid,
    auditStatus: AuditStatus.PASSED,
    auditRemark: null,
  }
}

/** 播放（仅 playable）。把可播放的收藏作为队列。 */
function playSong(s: FavoriteSongVO) {
  if (!s.playable) {
    ElMessage.warning('该歌曲已下架，无法播放')
    return
  }
  const queue = list.value.filter((x) => x.playable).map(toSongVO)
  player.play(toSongVO(s), queue)
}

function goDetail(s: FavoriteSongVO) {
  router.push(`/songs/${s.sid}`)
}

/** 取消收藏。 */
async function onUnfav(s: FavoriteSongVO) {
  await ElMessageBox.confirm(`取消收藏《${s.title}》？`, '提示', { type: 'warning' })
  await unfavorite(s.sid)
  ElMessage.success('已取消收藏')
  await load()
}
</script>

<template>
  <div class="favorites app-page">
    <section class="page-hero p-6 mb-6">
      <span class="hero-kicker">Favorites</span>
      <h1 class="hero-title mt-4" style="font-size: clamp(1.5rem, 3vw, 2.4rem)">我的收藏</h1>
      <p class="hero-subtitle mt-2">你收藏过的歌曲，按时间倒序展示。</p>
    </section>
    <el-card>
      <template #header><span>我的收藏（{{ total }}）</span></template>

      <el-empty v-if="!loading && list.length === 0" description="还没有收藏任何歌曲" />

      <el-table v-else v-loading="loading" :data="list">
        <el-table-column label="封面" width="72">
          <template #default="{ row }">
            <el-image :src="row.cover ?? undefined" fit="cover" class="cover" :class="{ gray: !row.playable }">
              <template #error><div class="cover-ph">🎵</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="180">
          <template #default="{ row }">
            <span :class="{ disabled: !row.playable }">{{ row.title }}</span>
            <el-tag v-if="!row.playable" size="small" type="info" class="off-tag">已下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ fmt(row.duration) }}</template>
        </el-table-column>
        <el-table-column prop="playCount" label="播放量" width="90" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              circle size="small" type="primary" :icon="'VideoPlay'"
              :disabled="!row.playable" @click="playSong(row)"
            />
            <el-button size="small" :disabled="!row.playable" @click="goDetail(row)">详情</el-button>
            <el-button size="small" type="danger" @click="onUnfav(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="list.length" class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total" :page-size="size" :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.favorites {
}
.cover {
  width: 48px;
  height: 48px;
  border-radius: 4px;
}
.cover.gray {
  filter: grayscale(1);
  opacity: 0.6;
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
.disabled {
  color: var(--el-text-color-secondary);
}
.off-tag {
  margin-left: 6px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
