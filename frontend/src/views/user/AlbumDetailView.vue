<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAlbumDetail } from '@/api/album'
import { usePlayerStore } from '@/stores/player'
import type { AlbumVO, SongVO } from '@/api/types'

/**
 * 专辑详情：专辑信息 + 其下口径A可见歌曲列表（可播放/进歌曲详情）。
 */
const route = useRoute()
const router = useRouter()
const player = usePlayerStore()

const aid = Number(route.params.aid)
const album = ref<AlbumVO | null>(null)
const songs = ref<SongVO[]>([])
const loading = ref(false)

function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    const res = await getAlbumDetail(aid)
    album.value = res.album
    songs.value = res.songs
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** 播放专辑内某歌，整张专辑作为队列。 */
function playSong(s: SongVO) {
  player.play(s, songs.value)
}
function goDetail(s: SongVO) {
  router.push(`/songs/${s.sid}`)
}
/** 播放整张专辑（从第一首）。 */
function playAll() {
  if (songs.value.length) player.play(songs.value[0], songs.value)
}
</script>

<template>
  <div class="album-detail" v-loading="loading">
    <el-card v-if="album" class="head-card">
      <div class="head">
        <el-image :src="album.cover ?? undefined" fit="cover" class="cover">
          <template #error><div class="cover-ph">💿</div></template>
        </el-image>
        <div class="info">
          <h2 class="name">{{ album.albumName }}</h2>
          <div class="sub">{{ album.releaseDate ?? '' }}</div>
          <p class="intro">{{ album.introduction ?? '暂无简介' }}</p>
          <el-button type="primary" :icon="'VideoPlay'" :disabled="songs.length === 0" @click="playAll">
            播放全部（{{ songs.length }}）
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="songs-card">
      <template #header><span>专辑曲目</span></template>
      <el-empty v-if="!loading && songs.length === 0" description="该专辑暂无可见歌曲" />
      <el-table v-else :data="songs" @row-click="playSong">
        <el-table-column label="#" type="index" width="60" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ fmt(row.duration) }}</template>
        </el-table-column>
        <el-table-column prop="playCount" label="播放量" width="100" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button circle size="small" type="primary" :icon="'VideoPlay'" @click.stop="playSong(row)" />
            <el-button size="small" @click.stop="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.album-detail {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.head {
  display: flex;
  gap: 20px;
}
.cover {
  width: 180px;
  height: 180px;
  border-radius: 8px;
  flex-shrink: 0;
}
.cover-ph {
  width: 180px;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.name {
  margin: 0;
}
.sub {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.intro {
  color: var(--el-text-color-regular);
  flex: 1;
}
.songs-card :deep(.el-table__row) {
  cursor: pointer;
}
</style>
