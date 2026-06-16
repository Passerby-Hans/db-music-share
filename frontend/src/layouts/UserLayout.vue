<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AudioPlayer from '@/components/AudioPlayer.vue'

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
      <div class="brand" @click="router.push('/songs')">
        <span class="brand-mark">♪</span>
        <span>在线音乐分享</span>
      </div>
      <el-menu mode="horizontal" :ellipsis="false" router class="nav">
        <el-menu-item index="/songs">歌曲广场</el-menu-item>
        <el-menu-item index="/albums">专辑</el-menu-item>
        <el-menu-item index="/favorites">我的收藏</el-menu-item>
        <el-menu-item index="/playlists">我的歌单</el-menu-item>
        <el-sub-menu v-if="auth.isUploader" index="uploader">
          <template #title>上传工作台</template>
          <el-menu-item index="/uploader/upload">上传歌曲</el-menu-item>
          <el-menu-item index="/uploader/songs">我的上传</el-menu-item>
          <el-menu-item index="/uploader/albums">我的专辑</el-menu-item>
        </el-sub-menu>
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
    <AudioPlayer />
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: transparent;
}
.header {
  position: sticky;
  top: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  gap: 20px;
  height: 72px;
  padding: 0 28px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 12px 38px rgba(15, 23, 42, 0.06);
  -webkit-backdrop-filter: blur(22px);
  backdrop-filter: blur(22px);
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #111827;
  font-size: 18px;
  font-weight: 850;
  cursor: pointer;
  white-space: nowrap;
}
.brand-mark {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: #fff;
  background: var(--brand-gradient);
  box-shadow: var(--shadow-button);
}
.nav {
  flex: 1;
  border-bottom: none;
  background: transparent;
}
.nav :deep(.el-menu-item),
.nav :deep(.el-sub-menu__title) {
  border-radius: 999px;
  color: #4b5563;
  font-weight: 650;
}
.nav :deep(.el-menu-item.is-active),
.nav :deep(.el-sub-menu.is-active .el-sub-menu__title) {
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.1);
  border-bottom-color: transparent;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.hello {
  color: #4b5563;
  font-size: 14px;
}
.main {
  min-height: calc(100vh - 72px);
  padding: 28px 0 112px;
  background: transparent;
}
@media (max-width: 980px) {
  .header {
    flex-wrap: wrap;
    height: auto;
    padding: 14px;
  }
  .nav {
    order: 3;
    width: 100%;
  }
}
</style>
