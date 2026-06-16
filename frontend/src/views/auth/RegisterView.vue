<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { register } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * 注册页：用户名/密码/昵称/邮箱（邮箱可选）。校验对齐后端约束。
 * 注册成功后用同一账号自动登录，跳首页。
 */
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '', email: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 50, message: '用户名 4~50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码 6~50 个字符', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 50, message: '昵称不超过 50 个字符', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

/** 提交注册，成功后自动登录。 */
async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      email: form.email || undefined,
    })
    ElMessage.success('注册成功，正在登录…')
    await auth.login({ username: form.username, password: form.password })
    router.replace('/songs')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <section class="auth-brand page-hero p-10">
      <span class="hero-kicker">Join Music Share</span>
      <h1 class="hero-title mt-6">创建账号，收藏你的音乐宇宙。</h1>
      <p class="hero-subtitle mt-5 max-w-xl">
        注册后即可浏览歌曲、参与评论评分、管理收藏歌单；上传者角色可继续发布音乐作品。
      </p>
      <div class="brand-orbit" aria-hidden="true">
        <div class="album album-a">♪</div>
        <div class="album album-b">▶</div>
        <div class="album album-c">✦</div>
      </div>
    </section>

    <el-card class="auth-card glass-card" body-style="padding: 34px">
      <div class="form-head">
        <span class="form-kicker">Join Music Share</span>
        <h2 class="auth-title">注册账号</h2>
        <p>填写基础信息，开启你的音乐分享空间。</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4~50 个字符" clearable size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6~50 个字符" show-password size="large" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示名称" clearable size="large" />
        </el-form-item>
        <el-form-item label="邮箱（可选）" prop="email">
          <el-input v-model="form.email" placeholder="用于找回等，可不填" clearable size="large" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="auth-submit" size="large" @click="onSubmit">
          注册并登录
        </el-button>
      </el-form>
      <div class="auth-foot">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-brand {
  min-height: 560px;
}
.brand-orbit {
  position: relative;
  height: 240px;
  margin-top: 42px;
}
.album {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 34px;
  font-weight: 900;
  box-shadow: 0 24px 80px rgba(99, 102, 241, 0.22);
}
.album-a {
  left: 6%;
  top: 18px;
  width: 148px;
  height: 148px;
  border-radius: 34px;
  background: linear-gradient(135deg, #38bdf8, #6366f1);
  transform: rotate(-8deg);
}
.album-b {
  left: 38%;
  top: 78px;
  width: 126px;
  height: 126px;
  border-radius: 30px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  transform: rotate(10deg);
}
.album-c {
  right: 8%;
  top: 16px;
  width: 110px;
  height: 110px;
  border-radius: 28px;
  background: linear-gradient(135deg, #a78bfa, #60a5fa);
  transform: rotate(-14deg);
}
.auth-card {
  width: min(430px, 100%);
  justify-self: center;
}
.form-head {
  margin-bottom: 28px;
}
.form-kicker {
  color: #6366f1;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.auth-title {
  margin: 8px 0;
  color: #111827;
  font-size: 30px;
  font-weight: 900;
  letter-spacing: -0.04em;
}
.form-head p {
  margin: 0;
  color: #6b7280;
}
.auth-submit {
  width: 100%;
  margin-top: 8px;
}
.auth-foot {
  margin-top: 18px;
  text-align: center;
  font-size: 14px;
  color: #6b7280;
}
.auth-foot a {
  color: #4f46e5;
  font-weight: 800;
  text-decoration: none;
}
@media (max-width: 900px) {
  .auth-brand {
    min-height: auto;
  }
  .brand-orbit {
    display: none;
  }
}
</style>
