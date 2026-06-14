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
  <div class="albums">
    <div class="toolbar">
      <el-input
        v-model="keyword" placeholder="搜索专辑名" clearable class="search"
        @keyup.enter="onSearch" @clear="onSearch"
      >
        <template #append><el-button :icon="'Search'" @click="onSearch" /></template>
      </el-input>
    </div>

    <div v-loading="loading" class="content">
      <el-empty v-if="!loading && albums.length === 0" description="暂无专辑" />
      <div v-else class="grid">
        <el-card
          v-for="a in albums" :key="a.aid" class="album-card" shadow="hover"
          body-style="padding:0" @click="goDetail(a)"
        >
          <el-image :src="a.cover ?? undefined" fit="cover" class="cover">
            <template #error><div class="cover-ph">💿</div></template>
          </el-image>
          <div class="card-body">
            <div class="name text-ellipsis">{{ a.albumName }}</div>
            <div class="intro text-ellipsis">{{ a.introduction ?? '—' }}</div>
          </div>
        </el-card>
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
.albums {
  max-width: 1100px;
  margin: 0 auto;
}
.toolbar {
  margin-bottom: 20px;
}
.search {
  max-width: 360px;
}
.content {
  min-height: 300px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.album-card {
  cursor: pointer;
  transition: transform 0.15s;
}
.album-card:hover {
  transform: translateY(-3px);
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
  background: var(--el-fill-color-light);
}
.card-body {
  padding: 10px 12px;
}
.name {
  font-weight: 600;
  font-size: 14px;
}
.intro {
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
  margin-top: 24px;
}
</style>
