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
    const redirect = (route.query.redirect as string) || '/home'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="auth-submit" @click="onSubmit">
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
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color-page);
}
.auth-card {
  width: 380px;
}
.auth-title {
  text-align: center;
  margin: 0 0 20px;
}
.auth-submit {
  width: 100%;
}
.auth-foot {
  margin-top: 16px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
