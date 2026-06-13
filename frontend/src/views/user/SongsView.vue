<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listPublicSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'
import type { SongVO } from '@/api/types'

/**
 * 歌曲广场（用户端默认首页）：搜索 + 卡片网格/列表行切换 + 分页。
 * 点歌交全局播放器 store，并把当前页全部歌曲作为播放队列（支持上一/下一首）。
 */
const player = usePlayerStore()

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

/** 该歌是否正在播放（高亮用）。 */
function isPlaying(song: SongVO): boolean {
  return player.current?.sid === song.sid
}
</script>

<template>
  <div class="songs">
    <!-- 工具栏：搜索 + 视图切换 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索歌曲标题"
        clearable
        class="search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #append>
          <el-button :icon="'Search'" @click="onSearch" />
        </template>
      </el-input>
      <el-radio-group v-model="view">
        <el-radio-button value="grid">网格</el-radio-button>
        <el-radio-button value="list">列表</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="content">
      <el-empty v-if="!loading && songs.length === 0" description="暂无歌曲" />

      <!-- 网格视图：封面卡片 -->
      <div v-else-if="view === 'grid'" class="grid">
        <el-card
          v-for="s in songs"
          :key="s.sid"
          class="song-card"
          :class="{ active: isPlaying(s) }"
          shadow="hover"
          body-style="padding:0"
          @click="playSong(s)"
        >
          <div class="cover-wrap">
            <el-image :src="s.cover ?? undefined" fit="cover" class="cover">
              <template #error><div class="cover-ph">🎵</div></template>
            </el-image>
            <div class="play-mask">
              <el-icon :size="36"><VideoPlay /></el-icon>
            </div>
          </div>
          <div class="card-body">
            <div class="title text-ellipsis">{{ s.title }}</div>
            <div class="meta">
              <span>{{ fmt(s.duration) }}</span>
              <span>▶ {{ s.playCount }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 列表视图：表格行 -->
      <el-table v-else :data="songs" class="list" @row-click="playSong">
        <el-table-column label="#" type="index" width="60" />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image :src="row.cover ?? undefined" fit="cover" class="list-cover">
              <template #error><div class="list-cover-ph">🎵</div></template>
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
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button circle size="small" type="primary" :icon="'VideoPlay'" @click.stop="playSong(row)" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
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
.songs {
  max-width: 1100px;
  margin: 0 auto;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.search {
  max-width: 360px;
}
.content {
  min-height: 300px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.song-card {
  cursor: pointer;
  transition: transform 0.15s;
}
.song-card:hover {
  transform: translateY(-3px);
}
.song-card.active {
  outline: 2px solid var(--el-color-primary);
}
.cover-wrap {
  position: relative;
  aspect-ratio: 1;
}
.cover {
  width: 100%;
  height: 100%;
  display: block;
}
.cover-ph,
.list-cover-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  background: var(--el-fill-color-light);
}
.play-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.15s;
}
.cover-wrap:hover .play-mask {
  opacity: 1;
}
.card-body {
  padding: 10px 12px;
}
.title {
  font-weight: 600;
  font-size: 14px;
}
.meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list-cover {
  width: 44px;
  height: 44px;
  border-radius: 4px;
}
.active-text {
  color: var(--el-color-primary);
  font-weight: 600;
}
.list :deep(.el-table__row) {
  cursor: pointer;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
