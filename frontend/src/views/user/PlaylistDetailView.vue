<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPlaylistDetail, removeSongFromPlaylist } from '@/api/playlist'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { AuditStatus, Role, type PlaylistSongVO, type PlaylistVO, type SongVO } from '@/api/types'

/**
 * 歌单详情：歌单信息 + 曲目列表（playable=false 置灰拦播）。
 * owner 或管理员可移除曲目。
 */
const route = useRoute()
const router = useRouter()
const player = usePlayerStore()
const auth = useAuthStore()

const plid = Number(route.params.plid)
const playlist = ref<PlaylistVO | null>(null)
const songs = ref<PlaylistSongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

/** 是否可管理（owner 或管理员）。 */
function canManage(): boolean {
  return playlist.value?.uid === auth.user?.uid || auth.user?.role === Role.ADMIN
}

async function load() {
  loading.value = true
  try {
    const res = await getPlaylistDetail(plid, page.value, size.value)
    playlist.value = res.playlist
    songs.value = res.songs.records
    total.value = res.songs.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onPageChange(p: number) {
  page.value = p
  load()
}

/** 曲目项转 SongVO（playable 必为已审核）。 */
function toSongVO(s: PlaylistSongVO): SongVO {
  return {
    sid: s.sid, title: s.title, cover: s.cover, duration: s.duration,
    playCount: s.playCount, albumAid: s.albumAid, uploaderUid: s.uploaderUid,
    auditStatus: AuditStatus.PASSED, auditRemark: null,
  }
}

function playSong(s: PlaylistSongVO) {
  if (!s.playable) {
    ElMessage.warning('该歌曲已下架，无法播放')
    return
  }
  const queue = songs.value.filter((x) => x.playable).map(toSongVO)
  player.play(toSongVO(s), queue)
}
function goDetail(s: PlaylistSongVO) {
  router.push(`/songs/${s.sid}`)
}

/** 从歌单移除曲目。 */
async function onRemove(s: PlaylistSongVO) {
  await ElMessageBox.confirm(`从歌单移除《${s.title}》？`, '提示', { type: 'warning' })
  await removeSongFromPlaylist(plid, s.sid)
  ElMessage.success('已移除')
  await load()
}
</script>

<template>
  <div class="pl-detail" v-loading="loading">
    <el-card v-if="playlist" class="head-card">
      <div class="head">
        <el-image :src="playlist.cover ?? undefined" fit="cover" class="cover">
          <template #error><div class="cover-ph">🎶</div></template>
        </el-image>
        <div class="info">
          <div class="name-row">
            <h2 class="name">{{ playlist.playlistName }}</h2>
            <el-tag size="small" :type="playlist.isPublic ? 'success' : 'info'">
              {{ playlist.isPublic ? '公开' : '私密' }}
            </el-tag>
          </div>
          <p class="desc">{{ playlist.description ?? '暂无简介' }}</p>
          <div class="count">{{ playlist.songCount }} 首歌曲</div>
        </div>
      </div>
    </el-card>

    <el-card class="songs-card">
      <template #header><span>曲目</span></template>
      <el-empty v-if="!loading && songs.length === 0" description="歌单还没有歌曲" />
      <el-table v-else :data="songs">
        <el-table-column label="#" type="index" width="60" />
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <span :class="{ disabled: !row.playable }">{{ row.title }}</span>
            <el-tag v-if="!row.playable" size="small" type="info" class="off-tag">已下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ fmt(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              circle size="small" type="primary" :icon="'VideoPlay'"
              :disabled="!row.playable" @click="playSong(row)"
            />
            <el-button size="small" :disabled="!row.playable" @click="goDetail(row)">详情</el-button>
            <el-button v-if="canManage()" size="small" type="danger" @click="onRemove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > size" class="pager">
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
.pl-detail {
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
  width: 160px;
  height: 160px;
  border-radius: 8px;
  flex-shrink: 0;
}
.cover-ph {
  width: 160px;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.info {
  flex: 1;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.name {
  margin: 0;
}
.desc {
  color: var(--el-text-color-regular);
}
.count {
  color: var(--el-text-color-secondary);
  font-size: 14px;
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
  margin-top: 16px;
}
</style>
