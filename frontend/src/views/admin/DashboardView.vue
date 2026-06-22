<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 管理后台概览页。进入此页已由路由守卫保证 role=2。
 * 三个卡片为各管理功能的入口。
 */
const auth = useAuthStore()
const router = useRouter()
</script>

<template>
  <div class="dashboard app-page-wide">
    <section class="page-hero p-8 mb-6">
      <span class="hero-kicker">Admin Console</span>
      <h1 class="hero-title mt-4">管理后台</h1>
      <p class="hero-subtitle mt-3">
        当前管理员：{{ auth.user?.nickname }}。集中处理歌曲审核、用户状态与存储维护。
      </p>
    </section>

    <div class="cards">
      <article class="entry section-card p-6" @click="router.push('/admin/audit')">
        <div class="entry-icon"><el-icon><Stamp /></el-icon></div>
        <h3>歌曲审核</h3>
        <p>待审列表 / 通过 / 驳回</p>
      </article>
      <article class="entry section-card p-6" @click="router.push('/admin/users')">
        <div class="entry-icon"><el-icon><User /></el-icon></div>
        <h3>用户管理</h3>
        <p>列表 / 封禁 / 解封 / 改角色</p>
      </article>
      <article class="entry section-card p-6" @click="router.push('/admin/storage')">
        <div class="entry-icon"><el-icon><FolderDelete /></el-icon></div>
        <h3>存储维护</h3>
        <p>孤儿文件扫描清理</p>
      </article>
      <article class="entry section-card p-6" @click="router.push('/admin/stats')">
        <div class="entry-icon"><el-icon><TrendCharts /></el-icon></div>
        <h3>统计报表</h3>
        <p>活跃度 / 贡献 / 播放量 TOP10</p>
      </article>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
}
.entry {
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease;
}
.entry:hover {
  transform: translateY(-5px);
  border-color: rgba(99, 102, 241, 0.36);
}
.entry-icon {
  display: inline-flex;
  width: 46px;
  height: 46px;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  color: #fff;
  background: var(--brand-gradient);
}
.entry h3 {
  margin: 16px 0 8px;
  color: #111827;
  font-size: 20px;
  font-weight: 850;
}
.entry p {
  margin: 0;
  color: #6b7280;
}
@media (max-width: 980px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
</style>
