<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAllComments } from '@/api/admin'
import { deleteComment } from '@/api/comment'
import type { CommentVO } from '@/api/types'

/**
 * 评论管理页（管理后台 role=2）：全站评论（主评论 + 回复），搜索 + 强删。
 * 强删复用 DELETE /api/comment/{cid}（管理员越权物理删 + CASCADE 删其下回复）。
 */
const comments = ref<CommentVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const keyword = ref('')

/** 时间格式化（ISO → 本地可读）。 */
function fmtTime(s: string): string {
  return s ? new Date(s).toLocaleString('zh-CN', { hour12: false }) : '—'
}

async function load() {
  loading.value = true
  try {
    const res = await listAllComments({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    comments.value = res.records
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

function onPageChange(p: number) {
  page.value = p
  load()
}

const acting = reactive<Record<number, boolean>>({})

/** 强删一条评论（物理删 + 级联其下回复），二次确认。 */
async function remove(c: CommentVO) {
  await ElMessageBox.confirm(
    '确定强删该评论？将一并级联删除其下所有回复，且不可恢复。',
    '强删确认',
    { type: 'warning' },
  )
  acting[c.cid] = true
  try {
    await deleteComment(c.cid)
    ElMessage.success('已删除')
    await load()
  } finally {
    acting[c.cid] = false
  }
}
</script>

<template>
  <div class="comments-mgmt app-page-wide">
    <section class="page-hero p-6 mb-6">
      <span class="hero-kicker">Content</span>
      <h1 class="hero-title mt-4" style="font-size: clamp(1.5rem, 3vw, 2.4rem)">评论管理</h1>
      <p class="hero-subtitle mt-2">全站评论：浏览、搜索、强删违规内容。</p>
    </section>

    <el-card>
      <template #header>
        <div class="head">
          <el-input
            v-model="keyword"
            placeholder="搜索评论内容"
            clearable
            class="search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-button :icon="'Refresh'" @click="load">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && comments.length === 0" description="暂无评论" />

      <el-table v-else v-loading="loading" :data="comments">
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="所属歌" min-width="160">
          <template #default="{ row }">
            <span>{{ row.songTitle ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="评论人" width="130" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              size="small"
              type="danger"
              :loading="acting[row.cid]"
              @click="remove(row)"
            >
              强删
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="comments.length" class="pager">
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
.head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search {
  max-width: 280px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
