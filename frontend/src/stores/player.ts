import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { getPlayUrl } from '@/api/song'
import type { SongVO } from '@/api/types'

/**
 * 全局播放器状态：跨页面共享，切换路由时音乐不中断。
 *
 * 设计：本 store 只管「放哪首、播放列表、当前预签名 URL、播放/暂停意图」；
 * 真正的 <audio> 元素由常驻在布局底部的 AudioPlayer 组件持有，监听这里的
 * currentUrl/playing 变化驱动播放。上一首/下一首基于 queue + index 计算。
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
        this.current = song
        this.currentUrl = url
        this.playing = true
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

    /** 切到上一首（队列循环）。 */
    prev(): void {
      if (this.queue.length < 2) return
      const i = (this.index - 1 + this.queue.length) % this.queue.length
      void this.play(this.queue[i], this.queue)
    },

    /** 切到下一首（队列循环）。 */
    next(): void {
      if (this.queue.length < 2) return
      const i = (this.index + 1) % this.queue.length
      void this.play(this.queue[i], this.queue)
    },

    /** 设置播放/暂停意图（由播放条按钮调用）。 */
    setPlaying(v: boolean): void {
      this.playing = v
    },
  },
})
