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
  <div class="dashboard">
    <el-alert
      title="管理后台"
      type="success"
      :closable="false"
      :description="`当前管理员：${auth.user?.nickname}。下方为各管理功能入口。`"
      show-icon
    />
    <el-row :gutter="16" class="cards">
      <el-col :span="8">
        <el-card shadow="hover" class="entry" @click="router.push('/admin/audit')">
          <el-statistic title="歌曲审核" value="进入" />
          <el-text type="info" size="small">待审列表 / 通过 / 驳回</el-text>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="entry" @click="router.push('/admin/users')">
          <el-statistic title="用户管理" value="进入" />
          <el-text type="info" size="small">列表 / 封禁 / 解封 / 改角色</el-text>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="entry" @click="router.push('/admin/storage')">
          <el-statistic title="存储维护" value="进入" />
          <el-text type="info" size="small">孤儿文件扫描清理</el-text>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.cards {
  margin-top: 8px;
}
.entry {
  cursor: pointer;
  transition: transform 0.15s;
}
.entry:hover {
  transform: translateY(-3px);
}
</style>
