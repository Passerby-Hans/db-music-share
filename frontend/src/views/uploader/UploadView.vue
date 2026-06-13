<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadRawFile,
  type UploadUserFile,
} from 'element-plus'
import { uploadAudio, uploadCover } from '@/api/upload'
import { uploadSong } from '@/api/song'
import { listMyAlbums } from '@/api/album'
import type { AlbumVO } from '@/api/types'

/**
 * 上传歌曲页：两步式——先传音频(必)/封面(可选)拿 key，再建歌。
 * 专辑归属三选一：缺省（系统按歌名建）/ 新建专辑 / 放入已有专辑。
 */
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

/** 专辑归属模式。 */
type AlbumMode = 'default' | 'new' | 'existing'

const form = reactive({
  title: '',
  duration: undefined as number | undefined,
  lyric: '',
  albumMode: 'default' as AlbumMode,
  newAlbumName: '',
  albumAid: undefined as number | undefined,
})

/** 暂存待上传的原始文件（提交时才真正上传）。 */
const audioFile = ref<File | null>(null)
const coverFile = ref<File | null>(null)
const coverPreview = ref<string>('')

/** 我的专辑（供「放入已有专辑」下拉，排除缺省专辑——缺省由系统托管）。 */
const myAlbums = ref<AlbumVO[]>([])

const rules: FormRules = {
  title: [
    { required: true, message: '请输入歌曲标题', trigger: 'blur' },
    { max: 150, message: '标题不超过 150 字符', trigger: 'blur' },
  ],
}

onMounted(async () => {
  const res = await listMyAlbums(1, 100)
  // 只列普通专辑供选择（缺省专辑随单曲生成，不该被手动复用）
  myAlbums.value = res.records.filter((a) => !a.isDefault)
})

/** 选音频：仅暂存，校验扩展名/大小。 */
function onAudioChange(file: UploadUserFile): boolean {
  const raw = file.raw as UploadRawFile
  const ext = raw.name.split('.').pop()?.toLowerCase() ?? ''
  const okExt = ['mp3', 'wav', 'flac', 'm4a', 'aac', 'ogg'].includes(ext)
  const okSize = raw.size <= 50 * 1024 * 1024
  if (!okExt) {
    ElMessage.error('音频格式仅支持 mp3/wav/flac/m4a/aac/ogg')
    return false
  }
  if (!okSize) {
    ElMessage.error('音频不能超过 50MB')
    return false
  }
  audioFile.value = raw
  return false // 阻止 el-upload 自动上传，提交时统一传
}

/** 选封面：暂存 + 本地预览。 */
function onCoverChange(file: UploadUserFile): boolean {
  const raw = file.raw as UploadRawFile
  const okExt = ['image/jpeg', 'image/png', 'image/gif'].includes(raw.type)
  const okSize = raw.size <= 5 * 1024 * 1024
  if (!okExt) {
    ElMessage.error('封面仅支持 jpg/png/gif')
    return false
  }
  if (!okSize) {
    ElMessage.error('封面不能超过 5MB')
    return false
  }
  coverFile.value = raw
  coverPreview.value = URL.createObjectURL(raw)
  return false
}

/** 提交：先传文件拿 key，再建歌。 */
async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (!audioFile.value) {
    ElMessage.error('请先选择音频文件')
    return
  }
  if (form.albumMode === 'new' && !form.newAlbumName.trim()) {
    ElMessage.error('请输入新专辑名')
    return
  }
  if (form.albumMode === 'existing' && !form.albumAid) {
    ElMessage.error('请选择已有专辑')
    return
  }

  submitting.value = true
  try {
    // 1. 传音频
    const audioRes = await uploadAudio(audioFile.value)
    // 2. 传封面（可选）
    let coverKey: string | undefined
    if (coverFile.value) {
      const coverRes = await uploadCover(coverFile.value)
      coverKey = coverRes.key ?? undefined
    }
    // 3. 建歌（按专辑模式组装）
    await uploadSong({
      title: form.title.trim(),
      audioPath: audioRes.key!,
      cover: coverKey,
      duration: form.duration,
      lyric: form.lyric || undefined,
      newAlbumName: form.albumMode === 'new' ? form.newAlbumName.trim() : undefined,
      albumAid: form.albumMode === 'existing' ? form.albumAid : undefined,
    })
    ElMessage.success('上传成功，已提交审核')
    router.push('/uploader/songs')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="upload">
    <el-card>
      <template #header><span>上传歌曲</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="音频文件" required>
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="onAudioChange" accept="audio/*">
            <el-button type="primary" plain>选择音频</el-button>
          </el-upload>
          <span v-if="audioFile" class="picked">已选：{{ audioFile.name }}</span>
        </el-form-item>

        <el-form-item label="封面">
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="onCoverChange" accept="image/*">
            <el-image v-if="coverPreview" :src="coverPreview" fit="cover" class="cover-preview" />
            <el-button v-else plain>选择封面（可选）</el-button>
          </el-upload>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="歌曲标题" maxlength="150" />
        </el-form-item>

        <el-form-item label="时长(秒)">
          <el-input-number v-model="form.duration" :min="1" placeholder="可选" />
        </el-form-item>

        <el-form-item label="歌词">
          <el-input v-model="form.lyric" type="textarea" :rows="4" placeholder="可选" />
        </el-form-item>

        <el-form-item label="所属专辑">
          <el-radio-group v-model="form.albumMode">
            <el-radio value="default">系统按歌名建</el-radio>
            <el-radio value="new">新建专辑</el-radio>
            <el-radio value="existing">已有专辑</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.albumMode === 'new'" label="新专辑名">
          <el-input v-model="form.newAlbumName" placeholder="专辑名" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="form.albumMode === 'existing'" label="选择专辑">
          <el-select v-model="form.albumAid" placeholder="选择我的专辑" class="album-select">
            <el-option v-for="a in myAlbums" :key="a.aid" :label="a.albumName" :value="a.aid" />
          </el-select>
          <span v-if="myAlbums.length === 0" class="hint">（你还没有普通专辑，先去专辑管理新建）</span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onSubmit">上传</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.upload {
  max-width: 680px;
  margin: 0 auto;
}
.picked {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.cover-preview {
  width: 120px;
  height: 120px;
  border-radius: 6px;
}
.album-select {
  width: 280px;
}
.hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
