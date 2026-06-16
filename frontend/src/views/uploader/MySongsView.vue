<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listMySongs, updateSong, deleteSong, moveSong } from '@/api/song'
import { listMyAlbums } from '@/api/album'
import { AuditStatus, type AlbumVO, type SongVO } from '@/api/types'

/**
 * 我的上传：列出本人所有歌（任意审核态），支持改元信息、移动到专辑、删除。
 * 审核态用标签区分：待审/通过/驳回（驳回显示理由）。
 */
const songs = ref<SongVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

/** 审核态 → 标签文案与类型。 */
function auditTag(s: SongVO): { text: string; type: 'info' | 'success' | 'danger' } {
  if (s.auditStatus === AuditStatus.PASSED) return { text: '已通过', type: 'success' }
  if (s.auditStatus === AuditStatus.REJECTED) return { text: '已驳回', type: 'danger' }
  return { text: '待审核', type: 'info' }
}

function fmt(sec: number | null): string {
  if (!sec) return '--:--'
  return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    const res = await listMySongs(page.value, size.value)
    songs.value = res.records
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

// —— 改歌弹窗 ——
const editVisible = ref(false)
const editRef = ref<FormInstance>()
const editForm = reactive({ sid: 0, title: '', duration: undefined as number | undefined, lyric: '' })
const editRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}
function openEdit(s: SongVO) {
  editForm.sid = s.sid
  editForm.title = s.title
  editForm.duration = s.duration ?? undefined
  editForm.lyric = ''
  editVisible.value = true
}
const savingEdit = ref(false)
async function saveEdit() {
  if (!editRef.value) return
  await editRef.value.validate()
  savingEdit.value = true
  try {
    await updateSong(editForm.sid, {
      title: editForm.title.trim(),
      duration: editForm.duration,
      lyric: editForm.lyric || undefined,
    })
    ElMessage.success('已保存，修改后需重新审核')
    editVisible.value = false
    await load()
  } finally {
    savingEdit.value = false
  }
}

// —— 移动到专辑弹窗 ——
const moveVisible = ref(false)
const moveSid = ref(0)
const moveTarget = ref<number>()
const myAlbums = ref<AlbumVO[]>([])
async function openMove(s: SongVO) {
  moveSid.value = s.sid
  moveTarget.value = undefined
  const res = await listMyAlbums(1, 100)
  myAlbums.value = res.records.filter((a) => !a.isDefault && a.aid !== s.albumAid)
  moveVisible.value = true
}
const movingSong = ref(false)
async function confirmMove() {
  if (!moveTarget.value) {
    ElMessage.error('请选择目标专辑')
    return
  }
  movingSong.value = true
  try {
    await moveSong(moveSid.value, moveTarget.value)
    ElMessage.success('已移动')
    moveVisible.value = false
    await load()
  } finally {
    movingSong.value = false
  }
}

// —— 删除 ——
async function onDelete(s: SongVO) {
  await ElMessageBox.confirm(`确定删除《${s.title}》？删除后音频文件一并清除。`, '删除确认', {
    type: 'warning',
  })
  await deleteSong(s.sid)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div class="my-songs app-page-wide">
    <section class="page-hero p-6 mb-6">
      <span class="hero-kicker">Creator Studio</span>
      <h1 class="hero-title mt-4" style="font-size: clamp(1.5rem, 3vw, 2.4rem)">我的上传</h1>
      <p class="hero-subtitle mt-2">管理已上传歌曲，编辑、移动专辑或删除（删除即清文件）。</p>
    </section>
    <el-card>
      <template #header>
        <div class="card-head">
          <span>我的上传</span>
          <el-button type="primary" @click="$router.push('/uploader/upload')">上传新歌</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="songs">
        <el-table-column label="封面" width="72">
          <template #default="{ row }">
            <el-image :src="row.cover ?? undefined" fit="cover" class="cover">
              <template #error><div class="cover-ph">🎵</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ fmt(row.duration) }}</template>
        </el-table-column>
        <el-table-column prop="playCount" label="播放量" width="90" />
        <el-table-column label="审核状态" width="160">
          <template #default="{ row }">
            <el-tag :type="auditTag(row).type" effect="light">{{ auditTag(row).text }}</el-tag>
            <el-tooltip v-if="row.auditRemark" :content="row.auditRemark" placement="top">
              <el-text type="danger" size="small" class="remark">理由</el-text>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="openMove(row)">移动</el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <!-- 改歌 -->
    <el-dialog v-model="editVisible" title="编辑歌曲" width="520px">
      <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="editForm.title" maxlength="150" />
        </el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="editForm.duration" :min="1" />
        </el-form-item>
        <el-form-item label="歌词">
          <el-input v-model="editForm.lyric" type="textarea" :rows="4" placeholder="留空则不改动展示" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon class="edit-tip">
        修改后歌曲将回到「待审核」，需管理员重新审核才会再次公开。
      </el-alert>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 移动到专辑 -->
    <el-dialog v-model="moveVisible" title="移动到专辑" width="420px">
      <el-select v-model="moveTarget" placeholder="选择目标专辑" style="width: 100%">
        <el-option v-for="a in myAlbums" :key="a.aid" :label="a.albumName" :value="a.aid" />
      </el-select>
      <el-text v-if="myAlbums.length === 0" type="info" size="small">没有可移动到的普通专辑</el-text>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :loading="movingSong" @click="confirmMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.my-songs {
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cover {
  width: 48px;
  height: 48px;
  border-radius: 4px;
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
.remark {
  margin-left: 6px;
  cursor: pointer;
  text-decoration: underline;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
.edit-tip {
  margin-top: 8px;
}
</style>
