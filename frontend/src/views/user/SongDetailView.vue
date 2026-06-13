<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSongDetail } from '@/api/song'
import { getRatingSummary, submitRating, removeRating } from '@/api/rating'
import { listSongComments, createComment, likeComment, unlikeComment, deleteComment } from '@/api/comment'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { Role, type CommentVO, type RatingSummaryVO, type SongDetailVO } from '@/api/types'

/**
 * 歌曲详情页：歌曲信息 + 歌词 + 评分 + 主评论区。
 * 评分用 el-rate（提交即 upsert，可撤销）；评论支持发表/点赞/删除（本人或管理员），本轮不含回复。
 */
const route = useRoute()
const player = usePlayerStore()
const auth = useAuthStore()

const sid = Number(route.params.sid)

const song = ref<SongDetailVO | null>(null)
const loadingSong = ref(false)

/** 时长秒 → mm:ss。 */
function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

async function loadSong() {
  loadingSong.value = true
  try {
    song.value = await getSongDetail(sid)
  } finally {
    loadingSong.value = false
  }
}

/** 播放本歌（单曲入队）。 */
function playThis() {
  if (song.value) player.play(song.value)
}

// —— 评分 ——
const rating = ref<RatingSummaryVO | null>(null)
/** 我的评分的本地绑定（el-rate v-model）。 */
const myRate = ref(0)

async function loadRating() {
  rating.value = await getRatingSummary(sid)
  myRate.value = rating.value.myScore ?? 0
}

/** 点星提交评分（upsert）。 */
async function onRate(score: number) {
  try {
    await submitRating(sid, score)
    ElMessage.success('评分成功')
    await loadRating()
  } catch {
    // 失败回滚显示
    await loadRating()
  }
}

/** 撤销我的评分。 */
async function onRemoveRate() {
  await removeRating(sid)
  ElMessage.success('已撤销评分')
  await loadRating()
}

// —— 评论 ——
const comments = ref<CommentVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loadingComments = ref(false)
const newContent = ref('')
const posting = ref(false)

async function loadComments() {
  loadingComments.value = true
  try {
    const res = await listSongComments(sid, page.value, size.value)
    comments.value = res.records
    total.value = res.total
  } finally {
    loadingComments.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  loadComments()
}

/** 发表主评论。 */
async function post() {
  const text = newContent.value.trim()
  if (!text) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  posting.value = true
  try {
    await createComment({ sid, content: text })
    ElMessage.success('已发表')
    newContent.value = ''
    page.value = 1
    await loadComments()
  } finally {
    posting.value = false
  }
}

/** 点赞/取消点赞（本地乐观更新 + 调接口）。 */
async function toggleLike(c: CommentVO) {
  try {
    if (c.likedByMe) {
      await unlikeComment(c.cid)
      c.likedByMe = false
      c.likeCount--
    } else {
      await likeComment(c.cid)
      c.likedByMe = true
      c.likeCount++
    }
  } catch {
    await loadComments()
  }
}

/** 删除评论（本人或管理员）。 */
async function onDeleteComment(c: CommentVO) {
  await ElMessageBox.confirm('确定删除这条评论？', '删除确认', { type: 'warning' })
  await deleteComment(c.cid)
  ElMessage.success('已删除')
  await loadComments()
}

/** 是否可删该评论（本人或管理员）。 */
function canDelete(c: CommentVO): boolean {
  return auth.user?.uid === c.uid || auth.user?.role === Role.ADMIN
}

const avgText = computed(() =>
  rating.value && rating.value.ratingCount > 0 ? rating.value.avgScore.toFixed(1) : '暂无',
)

onMounted(() => {
  loadSong()
  loadRating()
  loadComments()
})
</script>

<template>
  <div class="detail">
    <!-- 歌曲信息 + 评分 -->
    <el-card v-loading="loadingSong" class="head-card">
      <div v-if="song" class="head">
        <el-image :src="song.cover ?? undefined" fit="cover" class="cover">
          <template #error><div class="cover-ph">🎵</div></template>
        </el-image>
        <div class="info">
          <h2 class="title">{{ song.title }}</h2>
          <div class="meta">
            <span>时长 {{ fmt(song.duration) }}</span>
            <span>播放量 {{ song.playCount }}</span>
          </div>
          <div class="rate-line">
            <span class="rate-label">评分</span>
            <el-rate
              v-model="myRate"
              :disabled="!auth.isLoggedIn"
              @change="onRate"
            />
            <span class="avg">均分 {{ avgText }}（{{ rating?.ratingCount ?? 0 }} 人）</span>
            <el-button
              v-if="rating?.myScore"
              link
              type="info"
              size="small"
              @click="onRemoveRate"
            >
              撤销评分
            </el-button>
          </div>
          <el-button type="primary" :icon="'VideoPlay'" @click="playThis">播放</el-button>
        </div>
      </div>
    </el-card>

    <!-- 歌词 -->
    <el-card class="lyric-card">
      <template #header><span>歌词</span></template>
      <pre v-if="song?.lyric" class="lyric">{{ song.lyric }}</pre>
      <el-empty v-else description="暂无歌词" :image-size="60" />
    </el-card>

    <!-- 评论 -->
    <el-card class="comment-card">
      <template #header><span>评论（{{ total }}）</span></template>

      <!-- 发表框 -->
      <div v-if="auth.isLoggedIn" class="post-box">
        <el-input
          v-model="newContent"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="说点什么…"
        />
        <el-button type="primary" :loading="posting" class="post-btn" @click="post">发表</el-button>
      </div>
      <el-alert v-else type="info" :closable="false" class="login-tip">登录后可发表评论</el-alert>

      <!-- 评论列表 -->
      <div v-loading="loadingComments" class="comment-list">
        <el-empty v-if="!loadingComments && comments.length === 0" description="还没有评论" />
        <div v-for="c in comments" :key="c.cid" class="comment-item">
          <el-avatar :size="40" :src="c.avatar ?? undefined">{{ c.nickname?.[0] }}</el-avatar>
          <div class="c-body">
            <div class="c-head">
              <span class="c-name">{{ c.nickname ?? '已注销用户' }}</span>
              <span class="c-time">{{ c.createTime?.slice(0, 19).replace('T', ' ') }}</span>
            </div>
            <div class="c-content">{{ c.content }}</div>
            <div class="c-actions">
              <span class="like" :class="{ liked: c.likedByMe }" @click="toggleLike(c)">
                ♥ {{ c.likeCount }}
              </span>
              <span class="reply-count">回复 {{ c.replyCount }}</span>
              <el-button
                v-if="canDelete(c)"
                link
                type="danger"
                size="small"
                @click="onDeleteComment(c)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="total > size" class="pager">
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
.detail {
  max-width: 820px;
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
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.title {
  margin: 0;
}
.meta {
  display: flex;
  gap: 20px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.rate-line {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rate-label {
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.avg {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.lyric {
  white-space: pre-wrap;
  font-family: inherit;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  margin: 0;
  max-height: 320px;
  overflow-y: auto;
}
.post-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
  margin-bottom: 16px;
}
.post-btn {
  align-self: flex-end;
}
.login-tip {
  margin-bottom: 16px;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.c-body {
  flex: 1;
}
.c-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.c-name {
  font-weight: 600;
  font-size: 14px;
}
.c-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.c-content {
  margin: 6px 0;
  line-height: 1.6;
}
.c-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.like {
  cursor: pointer;
  user-select: none;
}
.like.liked {
  color: var(--el-color-danger);
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
