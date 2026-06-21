<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPublicAlbums } from '@/api/album'
import type { AlbumSort } from '@/api/album'
import type { AlbumVO } from '@/api/types'

/**
 * 专辑广场：公开专辑网格 + 搜索 + 分页。点卡片进专辑详情。
 */
const router = useRouter()

const albums = ref<AlbumVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const loading = ref(false)
/**
 * 排序维度：最近添加(空串哨兵=不传 sort，走后端 aid 默认) / 最新发布(release_date)，
 * 默认「最近添加」以保持原落地顺序。后端 desc 固定。
 */
const sort = ref<AlbumSort | ''>('')
/** 排序下拉选项。 */
const sortOptions: { label: string; value: AlbumSort | '' }[] = [
  { label: '最近添加', value: '' },
  { label: '最新发布', value: 'release_date' },
]

/** 发行日期格式化：取 YYYY-MM-DD 前 10 位，空 → —。 */
function fmtDate(s: string | null): string {
  return s ? s.slice(0, 10) : '—'
}

async function load() {
  loading.value = true
  try {
    const res = await listPublicAlbums(
      keyword.value,
      page.value,
      size.value,
      sort.value || undefined,
    )
    albums.value = res.records
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

/** 切换排序：回第一页重新查。排序与搜索相互独立。 */
function onSortChange() {
  page.value = 1
  load()
}
function onPageChange(p: number) {
  page.value = p
  load()
}
function goDetail(a: AlbumVO) {
  router.push(`/albums/${a.aid}`)
}
</script>

<template>
  <div class="albums app-page">
    <section class="page-hero p-7 mb-6">
      <span class="hero-kicker">Albums</span>
      <h1 class="hero-title mt-4">专辑广场</h1>
      <p class="hero-subtitle mt-3">浏览公开专辑，点封面查看完整曲目与介绍。</p>
    </section>
    <div class="toolbar toolbar-card p-4">
      <el-input
        v-model="keyword" placeholder="搜索专辑名" clearable class="search"
        @keyup.enter="onSearch" @clear="onSearch"
      >
        <template #append><el-button :icon="'Search'" @click="onSearch" /></template>
      </el-input>
      <el-select
        v-model="sort"
        size="large"
        class="sort"
        @change="onSortChange"
      >
        <el-option
          v-for="o in sortOptions"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
    </div>

    <div v-loading="loading" class="content mt-6">
      <el-empty v-if="!loading && albums.length === 0" description="暂无专辑" />
      <div v-else class="grid">
        <article
          v-for="a in albums" :key="a.aid" class="album-card music-card"
          @click="goDetail(a)"
        >
          <el-image :src="a.cover ?? undefined" fit="cover" class="cover">
            <template #error><div class="cover-ph">💿</div></template>
          </el-image>
          <div class="card-body">
            <div class="name text-ellipsis">{{ a.albumName }}</div>
            <div class="intro text-ellipsis">{{ a.introduction ?? '—' }}</div>
            <div class="date">💿 发行 {{ fmtDate(a.releaseDate) }}</div>
          </div>
        </article>
      </div>
    </div>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total" :page-size="size" :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.search {
  max-width: 360px;
}
.sort {
  width: 150px;
}
.content {
  min-height: 300px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 18px;
}
.album-card {
  cursor: pointer;
}
.cover {
  width: 100%;
  aspect-ratio: 1;
  display: block;
}
.cover-ph {
  width: 100%;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  color: #4f46e5;
  background: linear-gradient(135deg, #dbeafe, #ede9fe);
}
.card-body {
  padding: 12px 14px;
}
.name {
  color: #111827;
  font-weight: 800;
  font-size: 14px;
}
.intro {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
.date {
  font-size: 12px;
  color: #6b7280;
  margin-top: 6px;
}
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 26px;
}
</style>
