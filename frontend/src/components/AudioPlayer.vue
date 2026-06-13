<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'

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

// 切歌：URL 变了就重新加载并按意图播放
watch(
  () => player.currentUrl,
  async (url) => {
    const el = audioRef.value
    if (!el || !url) return
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

/** 一曲播完自动下一首。 */
function onEnded() {
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
  left: 0;
  right: 0;
  bottom: 0;
  height: 72px;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  background: #fff;
  border-top: 1px solid var(--el-border-color);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
  z-index: 1000;
}
.info {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 240px;
  cursor: pointer;
}
.cover {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  flex-shrink: 0;
}
.cover-ph {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.meta {
  min-width: 0;
}
.title {
  font-weight: 600;
  font-size: 14px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
.progress {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bar {
  flex: 1;
}
.time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  width: 42px;
  text-align: center;
}
.volume {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 140px;
}
.vol-bar {
  flex: 1;
}
</style>
