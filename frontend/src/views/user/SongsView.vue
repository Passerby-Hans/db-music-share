<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPublicSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'
import type { SongVO } from '@/api/types'

/**
 * 歌曲广场（用户端默认首页）：搜索 + 卡片网格/列表行切换 + 分页。
 * 点歌交全局播放器 store，并把当前页全部歌曲作为播放队列（支持上一/下一首）。
 * 点「详情」进歌曲详情页（歌词/评分/评论）。
 */
const player = usePlayerStore()
const router = useRouter()

const songs = ref<SongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const loading = ref(false)
/** 展示模式：网格 grid / 列表 list，用户可切换。 */
const view = ref<'grid' | 'list'>('grid')

/** 时长秒 → mm:ss。 */
function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

/** 拉取当前页歌曲。 */
async function load() {
  loading.value = true
  try {
    const res = await listPublicSongs(keyword.value, page.value, size.value)
    songs.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 搜索：回到第一页重新查。 */
function onSearch() {
  page.value = 1
  load()
}

/** 翻页。 */
function onPageChange(p: number) {
  page.value = p
  load()
}

/** 点歌播放：把当前页作为播放队列传入。 */
function playSong(song: SongVO) {
  player.play(song, songs.value)
}

/** 进入歌曲详情页。 */
function goDetail(song: SongVO) {
  router.push(`/songs/${song.sid}`)
}

/** 该歌是否正在播放（高亮用）。 */
function isPlaying(song: SongVO): boolean {
  return player.current?.sid === song.sid
}
</script>

<template>
  <div class="songs app-page">
    <section class="page-hero hero p-8">
      <div>
        <span class="hero-kicker">Discover</span>
        <h1 class="hero-title mt-5">发现今天想听的歌</h1>
        <p class="hero-subtitle mt-4 max-w-2xl">
          搜索已审核公开的音乐，切换网格或列表视图，点击封面即可开始播放。
        </p>
      </div>
      <div class="hero-disc" aria-hidden="true">♪</div>
    </section>

    <div class="toolbar toolbar-card mt-6 p-4">
      <el-input
        v-model="keyword"
        placeholder="搜索歌曲标题"
        clearable
        class="search"
        size="large"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #append>
          <el-button :icon="'Search'" @click="onSearch" />
        </template>
      </el-input>
      <el-radio-group v-model="view" size="large">
        <el-radio-button value="grid">网格</el-radio-button>
        <el-radio-button value="list">列表</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="content mt-6">
      <el-empty v-if="!loading && songs.length === 0" description="暂无歌曲" />

      <div v-else-if="view === 'grid'" class="grid">
        <article
          v-for="s in songs"
          :key="s.sid"
          class="song-card music-card"
          :class="{ active: isPlaying(s) }"
          @click="playSong(s)"
        >
          <div class="cover-wrap">
            <el-image :src="s.cover ?? undefined" fit="cover" class="cover">
              <template #error><div class="cover-ph">♪</div></template>
            </el-image>
            <div class="play-mask">
              <el-icon :size="38"><VideoPlay /></el-icon>
            </div>
          </div>
          <div class="card-body">
            <div class="title text-ellipsis">{{ s.title }}</div>
            <div class="meta">
              <span>{{ fmt(s.duration) }}</span>
              <span>▶ {{ s.playCount }}</span>
            </div>
            <el-button link type="primary" size="small" class="detail-link" @click.stop="goDetail(s)">
              查看详情
            </el-button>
          </div>
        </article>
      </div>

      <div v-else class="section-card list-wrap p-3">
        <el-table :data="songs" class="list" @row-click="playSong">
          <el-table-column label="#" type="index" width="60" />
          <el-table-column label="封面" width="82">
            <template #default="{ row }">
              <el-image :src="row.cover ?? undefined" fit="cover" class="list-cover">
                <template #error><div class="list-cover-ph">♪</div></template>
              </el-image>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="200">
            <template #default="{ row }">
              <span :class="{ 'active-text': isPlaying(row) }">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="100">
            <template #default="{ row }">{{ fmt(row.duration) }}</template>
          </el-table-column>
          <el-table-column prop="playCount" label="播放量" width="100" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button circle size="small" type="primary" :icon="'VideoPlay'" @click.stop="playSong(row)" />
              <el-button size="small" @click.stop="goDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
}
.hero-disc {
  width: 160px;
  height: 160px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 42px;
  color: #fff;
  font-size: 64px;
  font-weight: 900;
  background: var(--brand-gradient);
  box-shadow: 0 24px 80px rgba(99, 102, 241, 0.22);
  transform: rotate(8deg);
}
.search {
  max-width: 420px;
}
.content {
  min-height: 320px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 18px;
}
.song-card {
  cursor: pointer;
}
.song-card.active {
  border-color: rgba(99, 102, 241, 0.56);
}
.cover-wrap {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
}
.cover {
  width: 100%;
  height: 100%;
  display: block;
  transition: transform 0.22s ease;
}
.song-card:hover .cover {
  transform: scale(1.045);
}
.cover-ph,
.list-cover-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4f46e5;
  font-size: 32px;
  background: linear-gradient(135deg, #dbeafe, #ede9fe);
}
.play-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.08), rgba(15, 23, 42, 0.46));
  opacity: 0;
  transition: opacity 0.18s ease;
}
.cover-wrap:hover .play-mask {
  opacity: 1;
}
.card-body {
  padding: 14px 15px 16px;
}
.title {
  color: #111827;
  font-weight: 800;
  font-size: 15px;
}
.meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
  margin-top: 6px;
}
.detail-link {
  margin-top: 8px;
  padding: 0;
  font-weight: 800;
}
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list-wrap {
  overflow: hidden;
}
.list-cover {
  width: 48px;
  height: 48px;
  border-radius: 16px;
}
.active-text {
  color: #4f46e5;
  font-weight: 800;
}
.list :deep(.el-table__row) {
  cursor: pointer;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 26px;
}
@media (max-width: 760px) {
  .hero {
    align-items: flex-start;
  }
  .hero-disc {
    display: none;
  }
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .search {
    max-width: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .song-card:hover .cover,
  .cover-wrap:hover .play-mask {
    transition: none;
  }
}
</style>
