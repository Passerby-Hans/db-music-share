<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listMyAlbums, createAlbum, updateAlbum, deleteAlbum } from '@/api/album'
import type { AlbumDTO, AlbumVO } from '@/api/types'

/**
 * 我的专辑管理：列表 + 新建 + 编辑 + 删除（级联软删其下歌曲）。
 * 缺省专辑（系统为单曲生成）标记 tag 且禁止改/删。
 */
const albums = ref<AlbumVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listMyAlbums(page.value, size.value)
    albums.value = res.records
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

// —— 新建/编辑弹窗（共用） ——
const dialogVisible = ref(false)
const isEdit = ref(false)
const editAid = ref(0)
const formRef = ref<FormInstance>()
const form = reactive<AlbumDTO>({ albumName: '', cover: '', releaseDate: '', introduction: '' })
const rules: FormRules = {
  albumName: [
    { required: true, message: '请输入专辑名', trigger: 'blur' },
    { max: 100, message: '专辑名不超过 100 字符', trigger: 'blur' },
  ],
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { albumName: '', cover: '', releaseDate: '', introduction: '' })
  dialogVisible.value = true
}
function openEdit(a: AlbumVO) {
  isEdit.value = true
  editAid.value = a.aid
  Object.assign(form, {
    albumName: a.albumName,
    cover: a.cover ?? '',
    releaseDate: a.releaseDate ?? '',
    introduction: a.introduction ?? '',
  })
  dialogVisible.value = true
}

const saving = ref(false)
async function save() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const dto: AlbumDTO = {
      albumName: form.albumName.trim(),
      releaseDate: form.releaseDate || undefined,
      introduction: form.introduction || undefined,
    }
    if (isEdit.value) {
      await updateAlbum(editAid.value, dto)
      ElMessage.success('已保存')
    } else {
      await createAlbum(dto)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(a: AlbumVO) {
  await ElMessageBox.confirm(
    `确定删除专辑《${a.albumName}》？其下所有歌曲将一并删除。`,
    '删除确认',
    { type: 'warning' },
  )
  await deleteAlbum(a.aid)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div class="my-albums">
    <el-card>
      <template #header>
        <div class="card-head">
          <span>我的专辑</span>
          <el-button type="primary" @click="openCreate">新建专辑</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="albums">
        <el-table-column prop="albumName" label="专辑名" min-width="180">
          <template #default="{ row }">
            {{ row.albumName }}
            <el-tag v-if="row.isDefault" size="small" type="info" class="def-tag">缺省</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="releaseDate" label="发行日期" width="130">
          <template #default="{ row }">{{ row.releaseDate ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="introduction" label="简介" min-width="200">
          <template #default="{ row }">{{ row.introduction ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <!-- 缺省专辑系统托管，禁改禁删 -->
            <el-button size="small" :disabled="row.isDefault" @click="openEdit(row)">编辑</el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="row.isDefault"
              @click="onDelete(row)"
            >
              删除
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑专辑' : '新建专辑'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="专辑名" prop="albumName">
          <el-input v-model="form.albumName" maxlength="100" />
        </el-form-item>
        <el-form-item label="发行日期">
          <el-date-picker v-model="form.releaseDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.introduction" type="textarea" :rows="3" placeholder="可选" />
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
.my-albums {
  max-width: 1000px;
  margin: 0 auto;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.def-tag {
  margin-left: 6px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
