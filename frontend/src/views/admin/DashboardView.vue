<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

/**
 * 管理后台首页占位。进入此页已由路由守卫保证 role=2。
 * 后续将加入：用户管理（列表/封禁/改角色）、歌曲审核等管理页。
 */
const auth = useAuthStore()
</script>

<template>
  <div class="dashboard">
    <el-alert
      title="管理后台已就绪"
      type="success"
      :closable="false"
      :description="`当前管理员：${auth.user?.nickname}。本阶段为骨架占位，后续接入用户管理与歌曲审核。`"
      show-icon
    />
    <el-row :gutter="16" class="cards">
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="用户管理" value="待接入" />
          <el-text type="info" size="small">列表 / 封禁 / 解封 / 改角色</el-text>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="歌曲审核" value="待接入" />
          <el-text type="info" size="small">待审列表 / 通过 / 驳回</el-text>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="存储维护" value="待接入" />
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
</style>
