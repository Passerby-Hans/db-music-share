<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getTopUploaders, getTopUsers } from '@/api/admin'
import { getRank } from '@/api/rank'
import type { RankItemVO, TopUploaderVO, TopUserVO } from '@/api/types'

// 按需注册用到的 echarts 模块（控制包体；不引入 vue-echarts 封装）
echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

/**
 * 统计报表页（2.0 前端 C 块，管理后台 role=2）。
 * 三张横向柱状图 TOP10：用户活跃度 / 上传者贡献 / 播放量（后者复用排行榜总榜）。
 */

/** 三张图的 DOM 容器（v-show 保持挂载，ref 稳定）。 */
const chartUsersEl = ref<HTMLDivElement>()
const chartUploadersEl = ref<HTMLDivElement>()
const chartPlaysEl = ref<HTMLDivElement>()

/** 数据。 */
const users = ref<TopUserVO[]>([])
const uploaders = ref<TopUploaderVO[]>([])
const plays = ref<RankItemVO[]>([])

/** 各卡加载态。 */
const loadingUsers = ref(false)
const loadingUploaders = ref(false)
const loadingPlays = ref(false)

/** 已创建的 echarts 实例（用于 resize / dispose）。 */
let charts: ReturnType<typeof echarts.init>[] = []

/**
 * 在给定容器渲染一张横向柱状图。
 * names[0]/values[0] 视为 TOP1；yAxis category 默认底→顶，反转使 TOP1 显示在顶部。
 */
function renderBar(
  el: HTMLDivElement,
  names: string[],
  values: number[],
  valueName: string,
): void {
  const yData = names.slice().reverse()
  const sData = values.slice().reverse()
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '5%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: yData },
    series: [
      {
        name: valueName,
        type: 'bar',
        data: sData,
        itemStyle: { color: '#6366f1', borderRadius: [0, 6, 6, 0] },
      },
    ],
  })
  charts.push(chart)
}

/** 拉取用户活跃度并渲染。 */
async function loadUsers() {
  loadingUsers.value = true
  try {
    users.value = await getTopUsers()
    await nextTick()
    const el = chartUsersEl.value
    if (users.value.length && el) {
      renderBar(
        el,
        users.value.map((u) => u.nickname),
        users.value.map((u) => u.playCount),
        '点唱次数',
      )
    }
  } finally {
    loadingUsers.value = false
  }
}

/** 拉取上传者贡献并渲染。 */
async function loadUploaders() {
  loadingUploaders.value = true
  try {
    uploaders.value = await getTopUploaders()
    await nextTick()
    const el = chartUploadersEl.value
    if (uploaders.value.length && el) {
      renderBar(
        el,
        uploaders.value.map((u) => u.nickname),
        uploaders.value.map((u) => u.totalPlayCount),
        '总播放量',
      )
    }
  } finally {
    loadingUploaders.value = false
  }
}

/** 拉取播放量 TOP10（复用总榜）并渲染。 */
async function loadPlays() {
  loadingPlays.value = true
  try {
    plays.value = await getRank('total')
    await nextTick()
    const el = chartPlaysEl.value
    if (plays.value.length && el) {
      renderBar(
        el,
        plays.value.map((p) => p.title),
        plays.value.map((p) => p.score),
        '播放次数',
      )
    }
  } finally {
    loadingPlays.value = false
  }
}

/** 窗口缩放：各图自适应。 */
function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  // 三接口并行加载，互不阻塞
  void loadUsers()
  void loadUploaders()
  void loadPlays()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<template>
  <div class="stats app-page-wide">
    <section class="page-hero p-8 mb-6">
      <span class="hero-kicker">Insights</span>
      <h1 class="hero-title mt-4">统计报表</h1>
      <p class="hero-subtitle mt-3">
        用户活跃度、上传者贡献、播放热度 TOP10（数据每小时刷新）。
      </p>
    </section>

    <div class="chart-card section-card p-5 mb-5">
      <h3 class="chart-title">用户活跃度 TOP10</h3>
      <div v-loading="loadingUsers" class="chart-box">
        <el-empty v-if="!loadingUsers && users.length === 0" description="暂无数据" />
        <div v-show="users.length > 0" ref="chartUsersEl" class="chart"></div>
      </div>
    </div>

    <div class="chart-card section-card p-5 mb-5">
      <h3 class="chart-title">上传者贡献 TOP10</h3>
      <div v-loading="loadingUploaders" class="chart-box">
        <el-empty v-if="!loadingUploaders && uploaders.length === 0" description="暂无数据" />
        <div v-show="uploaders.length > 0" ref="chartUploadersEl" class="chart"></div>
      </div>
    </div>

    <div class="chart-card section-card p-5">
      <h3 class="chart-title">播放量 TOP10</h3>
      <div v-loading="loadingPlays" class="chart-box">
        <el-empty v-if="!loadingPlays && plays.length === 0" description="暂无数据" />
        <div v-show="plays.length > 0" ref="chartPlaysEl" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chart-title {
  margin: 0 0 14px;
  color: #111827;
  font-size: 17px;
  font-weight: 800;
}
.chart-box {
  position: relative;
  min-height: 360px;
}
.chart {
  height: 360px;
  width: 100%;
}
</style>
