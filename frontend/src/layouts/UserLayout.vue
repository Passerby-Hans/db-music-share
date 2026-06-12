<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/**
 * 用户端布局外壳：顶栏（站点名 + 导航 + 用户区）+ 内容路由出口。
 * 管理员额外显示「管理后台」入口。
 */
const auth = useAuthStore()
const router = useRouter()

const nickname = computed(() => auth.user?.nickname ?? '用户')

/** 登出确认后清态跳登录。 */
async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand" @click="router.push('/home')">🎵 在线音乐分享</div>
      <el-menu mode="horizontal" :ellipsis="false" router class="nav">
        <el-menu-item index="/home">首页</el-menu-item>
        <el-menu-item index="/profile">个人中心</el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/dashboard">管理后台</el-menu-item>
      </el-menu>
      <div class="user-area">
        <span class="hello">你好，{{ nickname }}</span>
        <el-button link type="primary" @click="handleLogout">退出</el-button>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  gap: 24px;
  border-bottom: 1px solid var(--el-border-color);
  background: #fff;
}
.brand {
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.nav {
  flex: 1;
  border-bottom: none;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hello {
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.main {
  background: var(--el-bg-color-page);
}
</style>
