<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listMyPlaylists, createPlaylist, updatePlaylist, deletePlaylist } from '@/api/playlist'
import type { PlaylistDTO, PlaylistVO } from '@/api/types'

/**
 * 我的歌单：列出本人全部歌单（含私密），支持新建/编辑/删除，点进详情管理曲目。
 */
const router = useRouter()

const list = ref<PlaylistVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listMyPlaylists(page.value, size.value)
    list.value = res.records
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
function goDetail(p: PlaylistVO) {
  router.push(`/playlists/${p.plid}`)
}

// —— 新建/编辑弹窗 ——
const dialogVisible = ref(false)
const isEdit = ref(false)
const editPlid = ref(0)
const formRef = ref<FormInstance>()
const form = reactive<PlaylistDTO>({ playlistName: '', description: '', cover: '', isPublic: true })
const rules: FormRules = {
  playlistName: [
    { required: true, message: '请输入歌单名', trigger: 'blur' },
    { max: 100, message: '不超过 100 字符', trigger: 'blur' },
  ],
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { playlistName: '', description: '', cover: '', isPublic: true })
  dialogVisible.value = true
}
function openEdit(p: PlaylistVO) {
  isEdit.value = true
  editPlid.value = p.plid
  Object.assign(form, {
    playlistName: p.playlistName,
    description: p.description ?? '',
    cover: p.cover ?? '',
    isPublic: p.isPublic,
  })
  dialogVisible.value = true
}

const saving = ref(false)
async function save() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const dto: PlaylistDTO = {
      playlistName: form.playlistName.trim(),
      description: form.description || undefined,
      isPublic: form.isPublic,
    }
    if (isEdit.value) {
      await updatePlaylist(editPlid.value, dto)
      ElMessage.success('已保存')
    } else {
      await createPlaylist(dto)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(p: PlaylistVO) {
  await ElMessageBox.confirm(`确定删除歌单《${p.playlistName}》？`, '删除确认', { type: 'warning' })
  await deletePlaylist(p.plid)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div class="playlists">
    <el-card>
      <template #header>
        <div class="head">
          <span>我的歌单（{{ total }}）</span>
          <el-button type="primary" @click="openCreate">新建歌单</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && list.length === 0" description="还没有歌单" />

      <div v-else v-loading="loading" class="grid">
        <el-card
          v-for="p in list" :key="p.plid" class="pl-card" shadow="hover" @click="goDetail(p)"
        >
          <div class="pl-top">
            <span class="pl-name text-ellipsis">{{ p.playlistName }}</span>
            <el-tag size="small" :type="p.isPublic ? 'success' : 'info'">
              {{ p.isPublic ? '公开' : '私密' }}
            </el-tag>
          </div>
          <div class="pl-desc text-ellipsis">{{ p.description ?? '—' }}</div>
          <div class="pl-foot">
            <span>{{ p.songCount }} 首</span>
            <span class="ops" @click.stop>
              <el-button link size="small" @click="openEdit(p)">编辑</el-button>
              <el-button link size="small" type="danger" @click="onDelete(p)">删除</el-button>
            </span>
          </div>
        </el-card>
      </div>

      <div v-if="list.length" class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total" :page-size="size" :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑歌单' : '新建歌单'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="playlistName">
          <el-input v-model="form.playlistName" maxlength="100" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="form.isPublic" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.playlists {
  max-width: 1000px;
  margin: 0 auto;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.pl-card {
  cursor: pointer;
  transition: transform 0.15s;
}
.pl-card:hover {
  transform: translateY(-3px);
}
.pl-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.pl-name {
  font-weight: 600;
  font-size: 15px;
}
.pl-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 8px 0;
}
.pl-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
