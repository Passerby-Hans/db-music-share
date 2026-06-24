<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/**
 * 登录页：用户名 + 密码，调 auth store 登录后按 redirect 回跳（默认首页）。
 * 字段校验对齐后端：用户名 4~50、密码 6~50。
 */
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 50, message: '用户名 4~50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码 6~50 个字符', trigger: 'blur' },
  ],
}

/** 提交登录。 */
async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    // 回跳到登录前想去的页面，否则首页
    const redirect = (route.query.redirect as string) || '/songs'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <section class="auth-brand page-hero p-10">
      <h1 class="hero-title">在线音乐分享系统</h1>
    </section>

    <el-card class="auth-card glass-card" body-style="padding: 34px">
      <div class="form-head">
        <h2 class="auth-title">登录账号</h2>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            size="large"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="auth-submit" size="large" @click="onSubmit">
          登录
        </el-button>
      </el-form>
      <div class="auth-foot">
        还没有账号？<router-link to="/register">去注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-brand {
  min-height: 560px;
}
.auth-card {
  width: min(430px, 100%);
  justify-self: center;
}
.form-head {
  margin-bottom: 28px;
}
.auth-title {
  margin: 8px 0;
  color: #111827;
  font-size: 30px;
  font-weight: 900;
  letter-spacing: -0.04em;
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
}
</style>
