<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getRank } from '@/api/rank'
import { usePlayerStore } from '@/stores/player'
import { AuditStatus, type RankBoard, type RankItemVO, type SongVO } from '@/api/types'

/**
 * 排行榜页（2.0 前端 B 块）：总/日/周榜 TOP10 分段切换。
 * 数据来自公开接口 GET /api/rank/{board}（Redis ZSET 缓存 + 降级聚合）。
 * 点歌接入全局播放器，当前榜 10 项映射为播放队列。
 */
const player = usePlayerStore()
const router = useRouter()

/** 当前榜单，默认总榜。 */
const board = ref<RankBoard>('total')
/** 榜单分段选项。 */
const boards: { label: string; value: RankBoard }[] = [
  { label: '总榜', value: 'total' },
  { label: '日榜', value: 'daily' },
  { label: '周榜', value: 'weekly' },
]
/** 当前榜单 TOP10。 */
const items = ref<RankItemVO[]>([])
const loading = ref(false)

/** 拉取当前榜单 TOP10。 */
async function load() {
  loading.value = true
  try {
    items.value = await getRank(board.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 切榜：v-model 已更新 board，重新查询。 */
function onBoardChange() {
  load()
}

/**
 * RankItemVO → SongVO 适配：sid/title/cover 真实，其余给无害默认值。
 * AudioPlayer 不读 song.duration（取自 <audio> 元数据）；playCount 用 0
 * （RankItemVO 不含总播放量，榜单播放数 score 在行内单独展示）。
 */
function toSongVO(item: RankItemVO): SongVO {
  return {
    sid: item.sid,
    title: item.title,
    cover: item.cover,
    duration: null,
    playCount: 0,
    albumAid: 0,
    uploaderUid: 0,
    auditStatus: AuditStatus.PASSED,
    auditRemark: null,
  }
}

/** 点播某首：当前榜 10 项映射为播放队列。 */
function playItem(item: RankItemVO) {
  player.play(toSongVO(item), items.value.map(toSongVO))
}

/** 进入歌曲详情页。 */
function goDetail(item: RankItemVO) {
  router.push(`/songs/${item.sid}`)
}

/** 该歌是否正在播放（高亮用）。 */
function isPlaying(item: RankItemVO): boolean {
  return player.current?.sid === item.sid
}

/** 名次徽章：1/2/3 用奖牌 emoji，其余显示数字。 */
function medal(rank: number): string {
  return (['🥇', '🥈', '🥉'] as const)[rank - 1] ?? String(rank)
}
</script>

<template>
  <div class="rank app-page">
    <section class="page-hero hero p-8">
      <div>
        <span class="hero-kicker">Charts</span>
        <h1 class="hero-title mt-5">排行榜</h1>
        <p class="hero-subtitle mt-4 max-w-2xl">
          看看大家都在听什么——总榜、日榜、周榜 TOP10，点击即可播放。
        </p>
      </div>
      <div class="hero-disc" aria-hidden="true">🏆</div>
    </section>

    <div class="toolbar toolbar-card mt-6 p-4">
      <el-radio-group v-model="board" size="large" @change="onBoardChange">
        <el-radio-button v-for="b in boards" :key="b.value" :value="b.value">
          {{ b.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="content section-card mt-6 p-3">
      <el-empty v-if="!loading && items.length === 0" description="暂无榜单数据" />

      <div v-else class="rank-list">
        <div
          v-for="item in items"
          :key="item.sid"
          class="rank-row"
          :class="{ active: isPlaying(item) }"
          @click="playItem(item)"
        >
          <div class="badge" :class="{ top: item.rank <= 3 }">{{ medal(item.rank) }}</div>
          <el-image :src="item.cover ?? undefined" fit="cover" class="cover">
            <template #error><div class="cover-ph">♪</div></template>
          </el-image>
          <div class="info">
            <div class="title text-ellipsis">{{ item.title }}</div>
            <div class="uploader">{{ item.uploaderName ?? '未知上传者' }}</div>
          </div>
          <div class="score">▶ {{ item.score }} 次</div>
          <div class="actions">
            <el-button circle type="primary" :icon="'VideoPlay'" @click.stop="playItem(item)" />
            <el-button size="small" @click.stop="goDetail(item)">详情</el-button>
          </div>
        </div>
      </div>
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
  background: var(--brand-gradient);
  box-shadow: 0 24px 80px rgba(99, 102, 241, 0.22);
  transform: rotate(8deg);
}
.content {
  min-height: 320px;
}
.rank-list {
  display: flex;
  flex-direction: column;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  cursor: pointer;
  transition: background 0.18s ease;
}
.rank-row:hover {
  background: rgba(99, 102, 241, 0.06);
}
.rank-row.active {
  background: rgba(99, 102, 241, 0.12);
}
.badge {
  width: 40px;
  flex-shrink: 0;
  text-align: center;
  font-size: 20px;
  font-weight: 850;
  color: #6b7280;
}
.badge.top {
  font-size: 26px;
}
.cover {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  flex-shrink: 0;
}
.cover-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: #4f46e5;
  font-size: 24px;
  background: linear-gradient(135deg, #dbeafe, #ede9fe);
}
.info {
  flex: 1;
  min-width: 0;
}
.title {
  color: #111827;
  font-weight: 800;
  font-size: 15px;
}
.uploader {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
.score {
  font-size: 13px;
  font-weight: 700;
  color: #4f46e5;
  white-space: nowrap;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 640px) {
  .score {
    display: none;
  }
}
</style>
