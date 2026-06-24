<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { usePlayerStore, type PlayMode } from '@/stores/player'
import { getFavoriteStatus, favorite, unfavorite } from '@/api/favorite'
import { listMyPlaylists, addSongToPlaylist } from '@/api/playlist'
import type { PlaylistVO } from '@/api/types'

/**
 * 全局底部播放条：常驻用户端布局底部，持有唯一的 <audio> 元素。
 *
 * 与 player store 的协作：
 * - 监听 store.currentUrl：变化时设 audio.src 并按 playing 意图播放；
 * - 监听 store.playing：驱动 audio.play()/pause()；
 * - audio 的 timeupdate/loadedmetadata 更新本地进度；用户拖动进度条 seek。
 * 「边放边加载」由浏览器对预签名 URL 的 HTTP Range 原生支持，无需额外处理。
 */
const player = usePlayerStore()
const router = useRouter()
const audioRef = ref<HTMLAudioElement>()

/** 各播放模式的 SVG 图标（path 数据，viewBox 0 0 1024 1024）与 tooltip 文案。 */
const MODE_META: Record<PlayMode, { label: string; viewBox: string; paths: string[] }> = {
  // 列表循环：双箭头环
  list: {
    label: '列表循环',
    viewBox: '0 0 1024 1024',
    paths: [
      'M694.4 854.4H195.2l48 44.8c9.6 6.4 16 16 16 28.8-3.2 19.2-19.2 32-38.4 32-9.6 0-22.4-6.4-28.8-12.8l-108.8-96c-12.8-12.8-16-35.2 0-48L192 704c6.4-6.4 19.2-9.6 28.8-9.6 19.2 0 35.2 16 35.2 35.2 0 9.6-6.4 19.2-12.8 25.6l-41.6 38.4h496c112 0 198.4-89.6 198.4-198.4v-86.4c0-19.2 12.8-32 32-32s32 12.8 32 32v86.4c0 140.8-118.4 259.2-265.6 259.2zM329.6 169.6h496l-48-44.8c-9.6-6.4-16-16-16-28.8 3.2-19.2 19.2-32 38.4-32 9.6 0 22.4 6.4 28.8 12.8l108.8 96c12.8 12.8 16 35.2 0 48L832 320c-6.4 6.4-19.2 9.6-28.8 9.6-19.2 0-35.2-16-35.2-35.2 0-9.6 6.4-19.2 12.8-25.6l41.6-38.4H326.4C217.6 233.6 128 323.2 128 435.2v89.6c0 19.2-12.8 32-32 32s-32-12.8-32-32v-86.4C64 288 182.4 169.6 329.6 169.6z',
    ],
  },
  // 单曲循环：双箭头环 + 数字 1（两条 path）
  one: {
    label: '单曲循环',
    viewBox: '0 0 1024 1024',
    paths: [
      'M928 476.8c-19.2 0-32 12.8-32 32v86.4c0 108.8-86.4 198.4-198.4 198.4H201.6l41.6-38.4c6.4-6.4 12.8-16 12.8-25.6 0-19.2-16-35.2-35.2-35.2-9.6 0-22.4 3.2-28.8 9.6l-108.8 99.2c-16 12.8-12.8 35.2 0 48l108.8 96c6.4 6.4 19.2 12.8 28.8 12.8 19.2 0 35.2-12.8 38.4-32 0-12.8-6.4-22.4-16-28.8l-48-44.8h499.2c147.2 0 265.6-118.4 265.6-259.2v-86.4c0-19.2-12.8-32-32-32zM96 556.8c19.2 0 32-12.8 32-32v-89.6c0-112 89.6-201.6 198.4-204.8h496l-41.6 38.4c-6.4 6.4-12.8 16-12.8 25.6 0 19.2 16 35.2 35.2 35.2 9.6 0 22.4-3.2 28.8-9.6l105.6-99.2c16-12.8 12.8-35.2 0-48l-108.8-96c-6.4-6.4-19.2-12.8-28.8-12.8-19.2 0-35.2 12.8-38.4 32 0 12.8 6.4 22.4 16 28.8l48 44.8H329.6C182.4 169.6 64 288 64 438.4v86.4c0 19.2 12.8 32 32 32z',
      'M544 672V352h-48L416 409.6l16 41.6 60.8-41.6V672z',
    ],
  },
  // 顺序播放：列表 + 右上箭头
  order: {
    label: '顺序播放',
    viewBox: '0 0 1024 1024',
    paths: [
      'M721.493333 212.992l0.213334-63.786667a21.418667 21.418667 0 0 1 12.16-19.328 21.077333 21.077333 0 0 1 22.613333 2.901334l174.549333 127.829333a21.333333 21.333333 0 0 1-13.610666 37.717333H85.333333v-85.333333h636.16zM85.333333 810.453333l853.333334-0.213333v85.333333l-853.333334 0.256v-85.333333z m0-298.709333h853.077334v85.333333H85.333333v-85.333333z',
    ],
  },
  // 随机播放：用户所给 path 仅含约 1.5 个箭头（缺左上臂），改用完整标准 shuffle 图标（24×24 viewBox）
  shuffle: {
    label: '随机播放',
    viewBox: '0 0 24 24',
    paths: [
      'M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 16.59 5.41 18 17.96 5.46 20 7.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z',
    ],
  },
}
/** 当前模式的展示信息（随 player.mode 变化）。 */
const modeMeta = computed(() => MODE_META[player.mode])

// —— 播放栏快捷操作：收藏 ——
/** 当前歌是否已收藏（切歌时查一次点亮）。 */
const isFav = ref(false)
const favLoading = ref(false)

// 切歌时刷新收藏态（无歌则复位）。播放条只在登录用户端布局出现，无需额外鉴权判断。
watch(
  () => player.current?.sid,
  async (sid) => {
    if (!sid) {
      isFav.value = false
      return
    }
    try {
      isFav.value = await getFavoriteStatus(sid)
    } catch {
      // 状态查询失败按未收藏显示；http 拦截器已处理报错
      isFav.value = false
    }
  },
  { immediate: true },
)

/** 切换收藏（幂等）。 */
async function toggleFav() {
  const sid = player.current?.sid
  if (!sid || favLoading.value) return
  favLoading.value = true
  try {
    if (isFav.value) {
      await unfavorite(sid)
      isFav.value = false
      ElMessage.success('已取消收藏')
    } else {
      await favorite(sid)
      isFav.value = true
      ElMessage.success('已收藏')
    }
  } finally {
    favLoading.value = false
  }
}

// —— 播放栏快捷操作：加入歌单 ——
const myPlaylists = ref<PlaylistVO[]>([])
const plLoading = ref(false)

/** popover 打开时拉「我的歌单」（每次打开都拉，保证新鲜）。 */
async function onPlaylistShow() {
  plLoading.value = true
  try {
    myPlaylists.value = (await listMyPlaylists(1, 100)).records
  } finally {
    plLoading.value = false
  }
}

/** 把当前歌加入选中的歌单（幂等）。失败由 http 拦截器弹消息。 */
async function addToPlaylist(pl: PlaylistVO) {
  const sid = player.current?.sid
  if (!sid) return
  await addSongToPlaylist(pl.plid, sid)
  ElMessage.success(`已加入《${pl.playlistName}》`)
}

/** 点歌名/封面进入该歌详情页。 */
function openDetail() {
  if (player.current) router.push(`/songs/${player.current.sid}`)
}

/** 当前播放秒数与总时长（秒）。 */
const currentTime = ref(0)
const duration = ref(0)
/** 音量 0~100。 */
const volume = ref(80)

/** 秒数格式化为 mm:ss。 */
function fmt(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// 切歌：URL 变了就重新加载并按意图播放。
// flush:'post' + nextTick 确保 <audio> 元素已渲染——首次点歌时播放条由 v-if 刚显示，
// audio 元素此刻才挂载，否则 audioRef 为空导致「首次点歌不播、要点两次」。
watch(
  () => player.currentUrl,
  async (url) => {
    if (!url) return
    await nextTick()
    const el = audioRef.value
    if (!el) return
    el.src = url
    currentTime.value = 0
    if (player.playing) {
      try {
        await el.play()
      } catch {
        // 自动播放被浏览器拦截时，等用户手动点播放
        player.setPlaying(false)
      }
    }
  },
  { flush: 'post' },
)

// 播放/暂停意图变化
watch(
  () => player.playing,
  (want) => {
    const el = audioRef.value
    if (!el || !el.src) return
    if (want) void el.play().catch(() => player.setPlaying(false))
    else el.pause()
  },
)

/** 点击播放条上的播放/暂停按钮。 */
function toggle() {
  if (!player.currentUrl) return
  player.setPlaying(!player.playing)
}

/** audio 元数据加载完成：拿到总时长。 */
function onLoaded() {
  duration.value = audioRef.value?.duration ?? 0
}

/** 播放进度更新。 */
function onTimeUpdate() {
  currentTime.value = audioRef.value?.currentTime ?? 0
}

/**
 * 一曲播完的自动续播：单曲循环原地重播（不重新埋点、不增播放量）；
 * 其余模式交给 player.next()（列表绕回 / 顺序到尾自然停 / 随机另一首）。
 */
function onEnded() {
  if (player.mode === 'one') {
    const el = audioRef.value
    if (el) {
      el.currentTime = 0
      void el.play().catch(() => player.setPlaying(false))
    }
    return
  }
  player.next()
}

/** 用户拖动进度条跳播。 */
function onSeek(val: number) {
  const el = audioRef.value
  if (el) el.currentTime = val
}

/** 调整音量（0~100 → 0~1）。 */
watch(volume, (v) => {
  const el = audioRef.value
  if (el) el.volume = v / 100
})
</script>

<template>
  <div v-if="player.hasCurrent" class="player-bar">
    <!-- 真正的音频元素（隐藏，由 store 驱动） -->
    <audio
      ref="audioRef"
      @loadedmetadata="onLoaded"
      @timeupdate="onTimeUpdate"
      @ended="onEnded"
    />

    <!-- 左：歌曲信息（点击进详情） -->
    <div class="info" @click="openDetail">
      <el-image :src="player.current?.cover ?? undefined" class="cover" fit="cover">
        <template #error><div class="cover-ph">🎵</div></template>
      </el-image>
      <div class="meta">
        <div class="title text-ellipsis">{{ player.current?.title }}</div>
        <div class="sub">播放量 {{ player.current?.playCount ?? 0 }}</div>
      </div>
    </div>

    <!-- 中：控制 + 进度 -->
    <div class="controls">
      <div class="btns">
        <el-tooltip :content="modeMeta.label" placement="top">
          <el-button circle class="mode-btn" :aria-label="modeMeta.label" @click="player.cycleMode()">
            <svg class="mode-ic" :viewBox="modeMeta.viewBox" aria-hidden="true">
              <path v-for="(d, i) in modeMeta.paths" :key="i" :d="d" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-button circle :icon="'ArrowLeftBold'" @click="player.prev()" />
        <el-button
          circle
          type="primary"
          size="large"
          :loading="player.loading"
          :icon="player.playing ? 'VideoPause' : 'VideoPlay'"
          @click="toggle"
        />
        <el-button circle :icon="'ArrowRightBold'" @click="player.next()" />
      </div>
      <div class="progress">
        <span class="time">{{ fmt(currentTime) }}</span>
        <el-slider
          :model-value="currentTime"
          :max="duration || 1"
          :show-tooltip="false"
          class="bar"
          @input="(v: number | number[]) => onSeek(v as number)"
        />
        <span class="time">{{ fmt(duration) }}</span>
      </div>
    </div>

    <!-- 右：快捷操作（收藏 / 加入歌单） -->
    <div class="actions">
      <el-tooltip :content="isFav ? '取消收藏' : '收藏'" placement="top">
        <el-button
          circle
          :icon="isFav ? 'StarFilled' : 'Star'"
          :class="{ faved: isFav }"
          :loading="favLoading"
          :disabled="!player.current"
          @click="toggleFav"
        />
      </el-tooltip>
      <el-popover placement="top" :width="240" trigger="click" @show="onPlaylistShow">
        <template #reference>
          <el-button circle :icon="'Plus'" :disabled="!player.current" />
        </template>
        <div v-loading="plLoading" class="pl-pop">
          <div class="pl-pop-title">加入歌单</div>
          <el-empty
            v-if="!plLoading && myPlaylists.length === 0"
            :image-size="50"
            description="还没有歌单"
          />
          <ul v-else class="pl-list">
            <li v-for="pl in myPlaylists" :key="pl.plid" @click="addToPlaylist(pl)">
              <span class="pl-nm text-ellipsis">{{ pl.playlistName }}</span>
              <span class="pl-cnt">{{ pl.songCount }} 首</span>
            </li>
          </ul>
        </div>
      </el-popover>
    </div>

    <!-- 右：音量 -->
    <div class="volume">
      <el-icon><Microphone /></el-icon>
      <el-slider v-model="volume" :show-tooltip="false" class="vol-bar" />
    </div>
  </div>
</template>

<style scoped>
.player-bar {
  position: fixed;
  left: 24px;
  right: 24px;
  bottom: 18px;
  height: 78px;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 22px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--shadow-float, 0 24px 80px rgba(15, 23, 42, 0.14));
  -webkit-backdrop-filter: blur(24px);
  backdrop-filter: blur(24px);
  z-index: 1000;
}
.info {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 260px;
  min-width: 0;
  cursor: pointer;
}
.cover {
  width: 54px;
  height: 54px;
  border-radius: 18px;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.16);
  flex-shrink: 0;
}
.cover-ph {
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  color: #4f46e5;
  background: linear-gradient(135deg, #dbeafe, #ede9fe);
}
.meta {
  min-width: 0;
}
.title {
  color: #111827;
  font-weight: 800;
  font-size: 14px;
}
.sub {
  font-size: 12px;
  color: #6b7280;
}
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.controls {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.mode-btn .mode-ic {
  width: 18px;
  height: 18px;
  fill: currentColor;
}
.progress {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bar {
  flex: 1;
}
.bar :deep(.el-slider__bar),
.vol-bar :deep(.el-slider__bar) {
  background: var(--brand-gradient);
}
.time {
  font-size: 12px;
  color: #6b7280;
  width: 42px;
  text-align: center;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.actions .faved {
  color: #f59e0b;
}
.pl-pop-title {
  font-weight: 800;
  font-size: 13px;
  color: #111827;
  margin-bottom: 8px;
}
.pl-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 240px;
  overflow-y: auto;
}
.pl-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
}
.pl-list li:hover {
  background: rgba(99, 102, 241, 0.08);
}
.pl-nm {
  font-size: 14px;
  color: #111827;
  min-width: 0;
}
.pl-cnt {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}
.volume {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 150px;
  color: #6366f1;
}
.vol-bar {
  flex: 1;
}
@media (max-width: 780px) {
  .player-bar {
    left: 12px;
    right: 12px;
    height: auto;
    min-height: 92px;
    flex-wrap: wrap;
    padding: 12px;
  }
  .info,
  .volume {
    width: auto;
  }
}
</style>
