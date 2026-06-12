<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 用户端首页占位。本阶段仅验证「登录后可见 + 布局正常」，
 * 业务内容（歌曲广场/歌单/播放等）留待后续模块阶段铺开。
 */
const auth = useAuthStore()
const nickname = computed(() => auth.user?.nickname ?? '')
</script>

<template>
  <div class="home">
    <el-result icon="success" title="欢迎回来" :sub-title="`你好，${nickname}！前端骨架已就绪。`">
      <template #extra>
        <el-space direction="vertical">
          <el-text type="info">本阶段交付：注册/登录、鉴权拦截、个人中心、分区路由。</el-text>
          <el-text type="info">后续将逐模块加入：歌曲广场、播放器、歌单、评论、收藏等。</el-text>
          <el-button type="primary" @click="$router.push('/profile')">去个人中心</el-button>
        </el-space>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.home {
  padding-top: 24px;
}
</style>
