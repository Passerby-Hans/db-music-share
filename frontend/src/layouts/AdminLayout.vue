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
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="title">管理后台</div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/audit">
          <el-icon><Stamp /></el-icon>
          <span>歌曲审核</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/storage">
          <el-icon><FolderDelete /></el-icon>
          <span>存储维护</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <el-button link @click="backToUser">← 返回用户端</el-button>
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
  background: #1d2129;
  display: flex;
  flex-direction: column;
}
.title {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  padding: 18px 20px;
}
.menu {
  flex: 1;
  border-right: none;
  background: transparent;
}
.aside-footer {
  padding: 16px 20px;
}
.aside-footer :deep(.el-button) {
  color: #c0c4cc;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color);
}
.admin-hello {
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.main {
  background: var(--el-bg-color-page);
}
</style>
