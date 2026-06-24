import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { getPlayUrl } from '@/api/song'
import { recordPlay } from '@/api/playRecord'
import type { SongVO } from '@/api/types'

/** 播放模式：列表循环 / 单曲循环 / 顺序(播完停) / 随机。 */
export type PlayMode = 'list' | 'one' | 'order' | 'shuffle'

/** localStorage 持久化键（刷新后保留播放模式）。 */
const MODE_KEY = 'player.mode'

/** 模式循环切换顺序：模式键每点一次按此前进一格。 */
const MODE_CYCLE: PlayMode[] = ['list', 'one', 'order', 'shuffle']

/** 读取已保存模式；缺省/非法值回退 'list'（= 2.0 队列循环行为）。 */
function loadMode(): PlayMode {
  const v = localStorage.getItem(MODE_KEY)
  return (MODE_CYCLE as string[]).includes(v ?? '') ? (v as PlayMode) : 'list'
}

/**
 * 全局播放器状态：跨页面共享，切换路由时音乐不中断。
 *
 * 设计：本 store 只管「放哪首、播放列表、当前预签名 URL、播放/暂停意图、播放模式」；
 * 真正的 <audio> 元素由常驻在布局底部的 AudioPlayer 组件持有，监听这里的
 * currentUrl/playing 变化驱动播放。上一首/下一首基于 queue + index + mode 计算。
 */
export const usePlayerStore = defineStore('player', {
  state: () => ({
    /** 当前播放歌曲；null 表示尚未播放任何歌。 */
    current: null as SongVO | null,
    /** 当前歌曲的预签名播放 URL（设给 audio.src）。 */
    currentUrl: '' as string,
    /** 播放队列（点歌时传入的列表，用于切换上一/下一首）。 */
    queue: [] as SongVO[],
    /** 当前歌在队列中的下标；-1 表示不在队列。 */
    index: -1,
    /** 播放意图：true 期望播放，false 期望暂停（AudioPlayer 据此 play/pause）。 */
    playing: false,
    /** 是否正在请求播放地址（防重复点击）。 */
    loading: false,
    /** 播放模式（初值取 localStorage，缺省列表循环=2.0 现状）。 */
    mode: loadMode(),
  }),

  getters: {
    /** 是否有歌已加载（用于决定是否渲染播放条）。 */
    hasCurrent: (state): boolean => state.current !== null,
  },

  actions: {
    /**
     * 播放指定歌曲：拉取预签名 URL 并设为当前曲目。
     * @param song 目标歌曲
     * @param queue 可选播放队列（如当前列表页的全部歌曲），用于上一/下一首
     */
    async play(song: SongVO, queue?: SongVO[]): Promise<void> {
      this.loading = true
      try {
        const url = await getPlayUrl(song.sid)
        // 点唱埋点：拿到播放地址=歌曲可见可播，上报一次。fire-and-forget——不 await、不阻断播放；
        // 失败由 http 拦截器处理。后端 60s 去重（同 uid+sid）防刷量，故前端每次 play() 如实上报即可。
        void recordPlay(song.sid)
        this.current = song
        this.currentUrl = url
        this.playing = true
        // 乐观反馈：播放条「播放量」即时 +1（DB 已事务内 +1；广场/详情页数字以 reload 为准）。
        song.playCount = (song.playCount ?? 0) + 1
        if (queue && queue.length) {
          this.queue = queue
          this.index = queue.findIndex((s) => s.sid === song.sid)
        } else {
          this.queue = [song]
          this.index = 0
        }
      } catch {
        // getPlayUrl 失败（如歌曲已下架 404）由 http 拦截器弹过消息，这里仅兜底
        ElMessage.error('无法播放该歌曲')
      } finally {
        this.loading = false
      }
    },

    /** 列表→单曲→顺序→随机 循环切换播放模式，并持久化到 localStorage。 */
    cycleMode(): void {
      const i = MODE_CYCLE.indexOf(this.mode)
      this.mode = MODE_CYCLE[(i + 1) % MODE_CYCLE.length]
      localStorage.setItem(MODE_KEY, this.mode)
    },

    /**
     * 随机选曲：在「除当前外的 n−1 首」里等概率取一个下标，绝不选回当前这首。
     * @returns 目标下标；队列 <2 首时返回当前下标（调用方已有 n<2 守卫）
     */
    randomIndex(): number {
      const n = this.queue.length
      if (n < 2) return this.index
      // (当前+1 起，偏移 0..n-2) % n —— 覆盖除当前外的全部 n-1 首，等概率且不重复当前
      return (this.index + 1 + Math.floor(Math.random() * (n - 1))) % n
    },

    /**
     * 上一首（手动）。
     * 随机→随机另一首；否则下标 -1，越界时：顺序停、列表/单曲绕回队尾。
     */
    prev(): void {
      const n = this.queue.length
      if (n < 2) return
      let i: number
      if (this.mode === 'shuffle') {
        i = this.randomIndex()
      } else {
        i = this.index - 1
        if (i < 0) {
          if (this.mode === 'order') return // 顺序：到头即停
          i = n - 1 // 列表/单曲：绕回队尾
        }
      }
      void this.play(this.queue[i], this.queue)
    },

    /**
     * 下一首。手动点击与「非单曲循环」的自动续播（AudioPlayer.onEnded）共用。
     * 随机→随机另一首；否则下标 +1，越界时：顺序停、列表/单曲绕回队首。
     */
    next(): void {
      const n = this.queue.length
      if (n < 2) return
      let i: number
      if (this.mode === 'shuffle') {
        i = this.randomIndex()
      } else {
        i = this.index + 1
        if (i >= n) {
          if (this.mode === 'order') return // 顺序：到尾即停
          i = 0 // 列表/单曲：绕回队首
        }
      }
      void this.play(this.queue[i], this.queue)
    },

    /** 设置播放/暂停意图（由播放条按钮调用）。 */
    setPlaying(v: boolean): void {
      this.playing = v
    },
  },
})
