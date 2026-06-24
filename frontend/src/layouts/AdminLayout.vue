<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/**
 * 管理后台布局外壳：左侧菜单 + 右侧内容区。
 * 进入本布局的路由已由路由守卫保证 role=2（requireAdmin）。
 */
const auth = useAuthStore()
const router = useRouter()

/** 返回用户端。 */
function backToUser() {
  router.push('/songs')
}

/** 登出。 */
async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="admin-shell layout">
    <el-aside width="232px" class="aside">
      <div class="title">
        <span class="title-mark">⌘</span>
        <span>管理后台</span>
      </div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/storage">
          <el-icon><FolderDelete /></el-icon>
          <span>存储维护</span>
        </el-menu-item>
        <el-menu-item index="/admin/stats">
          <el-icon><TrendCharts /></el-icon>
          <span>统计报表</span>
        </el-menu-item>
        <el-menu-item index="/admin/songs">
          <el-icon><Document /></el-icon>
          <span>歌曲管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/comments">
          <el-icon><ChatDotRound /></el-icon>
          <span>评论管理</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <el-button class="back-btn" link @click="backToUser">← 返回用户端</el-button>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="admin-hello">管理员：{{ auth.user?.nickname }}</span>
        <el-button link type="primary" @click="handleLogout">退出</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  display: flex;
  flex-direction: column;
  margin: 18px 0 18px 18px;
  border: 1px solid var(--border-soft);
  border-radius: 28px;
  background: var(--surface-glass);
  box-shadow: var(--shadow-soft);
  -webkit-backdrop-filter: blur(22px);
  backdrop-filter: blur(22px);
}
.title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #111827;
  font-size: 18px;
  font-weight: 850;
  padding: 20px;
}
.title-mark {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: #fff;
  background: var(--brand-gradient);
}
.menu {
  flex: 1;
  border-right: none;
  padding: 0 12px;
  background: transparent;
}
.menu :deep(.el-menu-item) {
  margin: 6px 0;
  border-radius: 16px;
  color: #4b5563;
  font-weight: 700;
}
.menu :deep(.el-menu-item.is-active) {
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.1);
}
.aside-footer {
  padding: 16px 20px 20px;
}
.back-btn {
  color: #4b5563;
  font-weight: 700;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  height: 72px;
  margin: 18px 18px 0;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: 0 12px 38px rgba(15, 23, 42, 0.06);
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}
.admin-hello {
  color: #4b5563;
  font-size: 14px;
  font-weight: 650;
}
.main {
  padding: 24px 18px 32px 24px;
  background: transparent;
}
</style>
