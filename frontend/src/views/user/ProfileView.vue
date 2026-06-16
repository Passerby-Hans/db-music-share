<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadRawFile,
} from 'element-plus'
import { getMe, updateProfile, changePassword, uploadAvatar } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { UserInfo } from '@/api/types'

/**
 * 个人中心：本阶段端到端联调的核心页。
 * 覆盖四条链路——拉取 /me、改昵称、改密码、上传头像，验证前后端打通。
 */
const auth = useAuthStore()
const me = ref<UserInfo | null>(null)
const loading = ref(false)

/** 角色码到中文。 */
const roleText = (r: number) => ['普通用户', '上传者', '管理员'][r] ?? '未知'

/** 拉取当前用户资料并同步到 store（昵称/头像可能已变）。 */
async function loadMe() {
  loading.value = true
  try {
    me.value = await getMe()
    auth.setUser(me.value)
  } finally {
    loading.value = false
  }
}

onMounted(loadMe)

// —— 改昵称 ——
const nickname = ref('')
const savingNick = ref(false)
async function saveNickname() {
  if (!nickname.value.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  savingNick.value = true
  try {
    await updateProfile(nickname.value.trim())
    ElMessage.success('昵称已更新')
    await loadMe()
  } finally {
    savingNick.value = false
  }
}

// —— 改密码 ——
const pwdRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '新密码 6~50 个字符', trigger: 'blur' },
  ],
}
const savingPwd = ref(false)
async function savePassword() {
  if (!pwdRef.value) return
  await pwdRef.value.validate()
  savingPwd.value = true
  try {
    await changePassword({ ...pwdForm })
    // 后端改密会作废全部会话；下次请求将遇 401 自动跳登录
    ElMessage.success('密码已修改，请重新登录')
  } finally {
    savingPwd.value = false
  }
}

// —— 传头像 ——
const uploading = ref(false)
/** 上传前校验类型与大小（前端先拦一道，后端还有内容校验）。 */
function beforeAvatarUpload(file: UploadRawFile): boolean {
  const okType = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type)
  const okSize = file.size <= 5 * 1024 * 1024
  if (!okType) ElMessage.error('仅支持 jpg/png/gif 图片')
  else if (!okSize) ElMessage.error('图片不能超过 5MB')
  return okType && okSize
}
/** 自定义上传：调头像接口，成功后刷新资料。 */
async function doUpload(file: File) {
  uploading.value = true
  try {
    const res = await uploadAvatar(file)
    ElMessage.success('头像已更新')
    if (me.value && res.url) me.value.avatar = res.url
    await loadMe()
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="profile" v-loading="loading">
    <section class="page-hero p-6 mb-6">
      <span class="hero-kicker">Profile</span>
      <h1 class="hero-title mt-4" style="font-size: clamp(1.5rem, 3vw, 2.4rem)">个人中心</h1>
      <p class="hero-subtitle mt-2">管理昵称、密码与头像。</p>
    </section>
    <el-card class="card">
      <template #header><span>账号信息</span></template>
      <div v-if="me" class="info">
        <el-avatar :size="72" :src="me.avatar ?? undefined">{{ me.nickname?.[0] }}</el-avatar>
        <el-descriptions :column="1" border class="desc">
          <el-descriptions-item label="用户名">{{ me.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ me.nickname }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ me.email ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleText(me.role) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-upload
        class="avatar-upload"
        :show-file-list="false"
        :before-upload="beforeAvatarUpload"
        :http-request="(opt: any) => doUpload(opt.file)"
      >
        <el-button :loading="uploading" type="primary" plain>更换头像</el-button>
      </el-upload>
    </el-card>

    <el-card class="card">
      <template #header><span>修改昵称</span></template>
      <el-input v-model="nickname" placeholder="输入新昵称（1~50 字符）" maxlength="50" class="nick-input" />
      <el-button type="primary" :loading="savingNick" @click="saveNickname">保存昵称</el-button>
    </el-card>

    <el-card class="card">
      <template #header><span>修改密码</span></template>
      <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="savingPwd" @click="savePassword">修改密码</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.profile {
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.info {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 16px;
}
.desc {
  flex: 1;
}
.nick-input {
  margin-bottom: 12px;
}
</style>
