<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSongDetail } from '@/api/song'
import { getAlbumDetail } from '@/api/album'
import { getRatingSummary, submitRating, removeRating } from '@/api/rating'
import { listSongComments, listReplies, createComment, likeComment, unlikeComment, deleteComment } from '@/api/comment'
import { getFavoriteStatus, favorite, unfavorite } from '@/api/favorite'
import { listMyPlaylists, addSongToPlaylist } from '@/api/playlist'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { Role, type CommentReplyVO, type CommentVO, type PlaylistVO, type RatingSummaryVO, type SongDetailVO } from '@/api/types'

/**
 * 歌曲详情页：歌曲信息 + 歌词 + 评分 + 主评论区。
 * 评分用 el-rate（提交即 upsert，可撤销）；评论支持发表/点赞/删除（本人或管理员），本轮不含回复。
 */
const route = useRoute()
const router = useRouter()
const player = usePlayerStore()
const auth = useAuthStore()

const sid = Number(route.params.sid)

const song = ref<SongDetailVO | null>(null)
const loadingSong = ref(false)
/** 所属专辑名（用 albumAid 查得，用于展示链接）。 */
const albumName = ref('')

/** 时长秒 → mm:ss。 */
function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

async function loadSong() {
  loadingSong.value = true
  try {
    song.value = await getSongDetail(sid)
    // 用 albumAid 查专辑名（SongDetailVO 只含 albumAid，名字另查）
    if (song.value?.albumAid) {
      try {
        const al = await getAlbumDetail(song.value.albumAid)
        albumName.value = al.album.albumName
      } catch {
        albumName.value = ''
      }
    }
  } finally {
    loadingSong.value = false
  }
}

/** 播放本歌（单曲入队）。 */
function playThis() {
  if (song.value) player.play(song.value)
}

// —— 收藏 ——
const faved = ref(false)
const favLoading = ref(false)

async function loadFavStatus() {
  if (!auth.isLoggedIn) return
  faved.value = await getFavoriteStatus(sid)
}

/** 切换收藏（幂等）。 */
async function toggleFav() {
  favLoading.value = true
  try {
    if (faved.value) {
      await unfavorite(sid)
      faved.value = false
      ElMessage.success('已取消收藏')
    } else {
      await favorite(sid)
      faved.value = true
      ElMessage.success('已收藏')
    }
  } finally {
    favLoading.value = false
  }
}

// —— 加入歌单 ——
const plDialog = ref(false)
const myPlaylists = ref<PlaylistVO[]>([])
const plLoading = ref(false)
const addingPlid = ref<number | null>(null)

/** 打开"加入歌单"弹窗，拉取我的歌单。 */
async function openAddToPlaylist() {
  plDialog.value = true
  plLoading.value = true
  try {
    const res = await listMyPlaylists(1, 100)
    myPlaylists.value = res.records
  } finally {
    plLoading.value = false
  }
}

/** 把当前歌加入选中的歌单。 */
async function addTo(p: PlaylistVO) {
  addingPlid.value = p.plid
  try {
    await addSongToPlaylist(p.plid, sid)
    ElMessage.success(`已加入《${p.playlistName}》`)
    plDialog.value = false
  } finally {
    addingPlid.value = null
  }
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

/** 点赞/取消点赞（主评论或回复均可，本地乐观更新 + 调接口）。 */
async function toggleLike(c: CommentVO | CommentReplyVO) {
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

/** 删除主评论（本人或管理员）。删主评论会级联删其回复。 */
async function onDeleteComment(c: CommentVO) {
  await ElMessageBox.confirm('确定删除这条评论？其下回复将一并删除。', '删除确认', { type: 'warning' })
  await deleteComment(c.cid)
  ElMessage.success('已删除')
  await loadComments()
}

/** 是否可删该评论/回复（本人或管理员）。 */
function canDelete(c: CommentVO | CommentReplyVO): boolean {
  return auth.user?.uid === c.uid || auth.user?.role === Role.ADMIN
}

// —— 回复（两层盖楼）——
/** 每条主评论的回复状态：是否展开、回复列表、回复输入框、发表中。 */
interface ReplyState {
  open: boolean
  list: CommentReplyVO[]
  input: string
  loading: boolean
  posting: boolean
}
const replyMap = reactive<Record<number, ReplyState>>({})

function ensureReplyState(cid: number): ReplyState {
  if (!replyMap[cid]) {
    replyMap[cid] = { open: false, list: [], input: '', loading: false, posting: false }
  }
  return replyMap[cid]
}

/** 展开/收起某主评论的回复；首次展开时拉取。 */
async function toggleReplies(c: CommentVO) {
  const st = ensureReplyState(c.cid)
  st.open = !st.open
  if (st.open && st.list.length === 0 && c.replyCount > 0) {
    st.loading = true
    try {
      const res = await listReplies(c.cid, 1, 100)
      st.list = res.records
    } finally {
      st.loading = false
    }
  }
}

/** 在某主评论下发表回复（parentCid 指向主评论）。 */
async function postReply(c: CommentVO) {
  const st = ensureReplyState(c.cid)
  const text = st.input.trim()
  if (!text) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  st.posting = true
  try {
    await createComment({ sid, content: text, parentCid: c.cid })
    ElMessage.success('已回复')
    st.input = ''
    // 重新拉该主评论的回复并刷新回复数
    const res = await listReplies(c.cid, 1, 100)
    st.list = res.records
    c.replyCount = res.total
    st.open = true
  } finally {
    st.posting = false
  }
}

/** 删除一条回复（本人或管理员），从本地列表移除并减回复数。 */
async function onDeleteReply(c: CommentVO, r: CommentReplyVO) {
  await ElMessageBox.confirm('确定删除这条回复？', '删除确认', { type: 'warning' })
  await deleteComment(r.cid)
  ElMessage.success('已删除')
  const st = ensureReplyState(c.cid)
  st.list = st.list.filter((x) => x.cid !== r.cid)
  c.replyCount = Math.max(0, c.replyCount - 1)
}

const avgText = computed(() =>
  rating.value && rating.value.ratingCount > 0 ? rating.value.avgScore.toFixed(1) : '暂无',
)

onMounted(() => {
  loadSong()
  loadRating()
  loadComments()
  loadFavStatus()
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
            <span v-if="albumName" class="album-link" @click="router.push(`/albums/${song.albumAid}`)">
              专辑：{{ albumName }}
            </span>
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
          <div class="action-row">
            <el-button type="primary" :icon="'VideoPlay'" @click="playThis">播放</el-button>
            <el-button
              v-if="auth.isLoggedIn"
              :type="faved ? 'danger' : 'default'"
              :loading="favLoading"
              @click="toggleFav"
            >
              {{ faved ? '♥ 已收藏' : '♡ 收藏' }}
            </el-button>
            <el-button v-if="auth.isLoggedIn" @click="openAddToPlaylist">加入歌单</el-button>
          </div>
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
              <span class="reply-toggle" @click="toggleReplies(c)">
                回复 {{ c.replyCount }}
                <span class="caret">{{ replyMap[c.cid]?.open ? '▲' : '▼' }}</span>
              </span>
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

            <!-- 回复区（展开时） -->
            <div v-if="replyMap[c.cid]?.open" class="reply-zone" v-loading="replyMap[c.cid]?.loading">
              <!-- 回复发表框（登录可见） -->
              <div v-if="auth.isLoggedIn" class="reply-post">
                <el-input
                  v-model="replyMap[c.cid].input"
                  size="small"
                  maxlength="500"
                  placeholder="回复…"
                  @keyup.enter="postReply(c)"
                />
                <el-button
                  size="small"
                  type="primary"
                  :loading="replyMap[c.cid].posting"
                  @click="postReply(c)"
                >
                  回复
                </el-button>
              </div>
              <!-- 回复列表 -->
              <div v-for="r in replyMap[c.cid].list" :key="r.cid" class="reply-item">
                <el-avatar :size="28" :src="r.avatar ?? undefined">{{ r.nickname?.[0] }}</el-avatar>
                <div class="r-body">
                  <div class="r-head">
                    <span class="r-name">{{ r.nickname ?? '已注销用户' }}</span>
                    <span class="r-time">{{ r.createTime?.slice(0, 19).replace('T', ' ') }}</span>
                  </div>
                  <div class="r-content">{{ r.content }}</div>
                  <div class="r-actions">
                    <span class="like" :class="{ liked: r.likedByMe }" @click="toggleLike(r)">
                      ♥ {{ r.likeCount }}
                    </span>
                    <el-button
                      v-if="canDelete(r)"
                      link
                      type="danger"
                      size="small"
                      @click="onDeleteReply(c, r)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
              <el-empty
                v-if="!replyMap[c.cid].loading && replyMap[c.cid].list.length === 0"
                description="还没有回复"
                :image-size="40"
              />
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

    <!-- 加入歌单弹窗 -->
    <el-dialog v-model="plDialog" title="加入歌单" width="420px">
      <div v-loading="plLoading">
        <el-empty v-if="!plLoading && myPlaylists.length === 0" description="还没有歌单，先去「我的歌单」创建" />
        <div v-for="p in myPlaylists" :key="p.plid" class="pl-row">
          <div class="pl-meta">
            <span class="pl-nm">{{ p.playlistName }}</span>
            <span class="pl-cnt">{{ p.songCount }} 首 · {{ p.isPublic ? '公开' : '私密' }}</span>
          </div>
          <el-button
            size="small" type="primary"
            :loading="addingPlid === p.plid"
            @click="addTo(p)"
          >
            加入
          </el-button>
        </div>
      </div>
    </el-dialog>
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
.album-link {
  color: var(--el-color-primary);
  cursor: pointer;
}
.action-row {
  display: flex;
  gap: 12px;
}
.pl-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.pl-meta {
  display: flex;
  flex-direction: column;
}
.pl-nm {
  font-weight: 600;
}
.pl-cnt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
.reply-toggle {
  cursor: pointer;
  user-select: none;
}
.caret {
  font-size: 10px;
}
.reply-zone {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
.reply-post {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.reply-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.reply-item:last-child {
  border-bottom: none;
}
.r-body {
  flex: 1;
}
.r-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.r-name {
  font-weight: 600;
  font-size: 13px;
}
.r-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.r-content {
  margin: 4px 0;
  font-size: 14px;
  line-height: 1.5;
}
.r-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
