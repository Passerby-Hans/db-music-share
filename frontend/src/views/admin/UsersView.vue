<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, banUser, unbanUser, changeUserRole } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { Role, UserStatus, type AdminUserVO } from '@/api/types'

/**
 * 用户管理页：列表 + 关键字/角色/状态筛选 + 封禁/解封 + 改角色。
 * 自我保护：不能对自己执行封禁/改角色（后端已拦，前端禁用本行危险操作）。
 */
const auth = useAuthStore()

const users = ref<AdminUserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

/** 筛选条件。 */
const filter = reactive({ keyword: '', role: undefined as number | undefined, status: undefined as number | undefined })

const roleText = (r: number) => ['普通用户', '上传者', '管理员'][r] ?? '未知'
const roleTagType = (r: number): 'info' | 'warning' | 'danger' =>
  r === Role.ADMIN ? 'danger' : r === Role.UPLOADER ? 'warning' : 'info'

/** 是否当前登录管理员本人（禁用对自己的危险操作）。 */
const isSelf = (u: AdminUserVO) => u.uid === auth.user?.uid

async function load() {
  loading.value = true
  try {
    const res = await listUsers({
      keyword: filter.keyword || undefined,
      role: filter.role,
      status: filter.status,
      page: page.value,
      size: size.value,
    })
    users.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onSearch() {
  page.value = 1
  load()
}
function onReset() {
  filter.keyword = ''
  filter.role = undefined
  filter.status = undefined
  onSearch()
}
function onPageChange(p: number) {
  page.value = p
  load()
}

const acting = reactive<Record<number, boolean>>({})

/** 封禁/解封切换。 */
async function toggleBan(u: AdminUserVO) {
  const banning = u.status === UserStatus.NORMAL
  await ElMessageBox.confirm(
    banning ? `确定封禁用户「${u.nickname}」？其登录会话将被即时作废。` : `确定解封用户「${u.nickname}」？`,
    banning ? '封禁确认' : '解封确认',
    { type: 'warning' },
  )
  acting[u.uid] = true
  try {
    if (banning) await banUser(u.uid)
    else await unbanUser(u.uid)
    ElMessage.success(banning ? '已封禁' : '已解封')
    await load()
  } finally {
    acting[u.uid] = false
  }
}

/** 改角色。 */
async function onRoleChange(u: AdminUserVO, newRole: number) {
  acting[u.uid] = true
  try {
    await changeUserRole(u.uid, newRole)
    ElMessage.success(`已将「${u.nickname}」设为${roleText(newRole)}，其会话已作废`)
    await load()
  } finally {
    acting[u.uid] = false
  }
}
</script>

<template>
  <div class="users">
    <el-card>
      <template #header><span>用户管理</span></template>

      <!-- 筛选栏 -->
      <div class="filters">
        <el-input
          v-model="filter.keyword"
          placeholder="用户名 / 昵称"
          clearable
          class="kw"
          @keyup.enter="onSearch"
        />
        <el-select v-model="filter.role" placeholder="角色" clearable class="sel">
          <el-option label="普通用户" :value="0" />
          <el-option label="上传者" :value="1" />
          <el-option label="管理员" :value="2" />
        </el-select>
        <el-select v-model="filter.status" placeholder="状态" clearable class="sel">
          <el-option label="正常" :value="0" />
          <el-option label="封禁" :value="1" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="users">
        <el-table-column prop="uid" label="uid" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160">
          <template #default="{ row }">{{ row.email ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" effect="light">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="danger">封禁</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <template v-if="isSelf(row)">
              <el-text type="info" size="small">（当前登录账号）</el-text>
            </template>
            <template v-else>
              <el-button
                size="small"
                :type="row.status === 0 ? 'danger' : 'success'"
                :loading="acting[row.uid]"
                @click="toggleBan(row)"
              >
                {{ row.status === 0 ? '封禁' : '解封' }}
              </el-button>
              <el-select
                :model-value="row.role"
                size="small"
                class="role-sel"
                :disabled="acting[row.uid]"
                @change="(v: number) => onRoleChange(row, v)"
              >
                <el-option label="普通用户" :value="0" />
                <el-option label="上传者" :value="1" />
                <el-option label="管理员" :value="2" />
              </el-select>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.kw {
  width: 200px;
}
.sel {
  width: 120px;
}
.role-sel {
  width: 120px;
  margin-left: 8px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
