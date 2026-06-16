<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPublicAlbums } from '@/api/album'
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

async function load() {
  loading.value = true
  try {
    const res = await listPublicAlbums(keyword.value, page.value, size.value)
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
