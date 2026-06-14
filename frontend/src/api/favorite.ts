import http from './http'
import type { FavoriteSongVO, PageResult } from './types'

/**
 * 收藏接口。全部需登录即可，不限角色。收藏/取消幂等。
 */

/**
 * 收藏一首歌（幂等，须公开可见）。
 * @param sid 歌曲 sid
 */
export function favorite(sid: number): Promise<void> {
  return http.post(`/favorite/${sid}`) as unknown as Promise<void>
}

/**
 * 取消收藏（幂等，已下架的歌也可取消）。
 * @param sid 歌曲 sid
 */
export function unfavorite(sid: number): Promise<void> {
  return http.delete(`/favorite/${sid}`) as unknown as Promise<void>
}

/**
 * 我的收藏分页（按收藏时间倒序，含失效歌但 playable=false）。
 * @param page 页码
 * @param size 每页条数
 */
export function listMyFavorites(page: number, size: number): Promise<PageResult<FavoriteSongVO>> {
  return http.get('/favorite/mine', {
    params: { page, size },
  }) as unknown as Promise<PageResult<FavoriteSongVO>>
}

/**
 * 查询是否已收藏某歌（供详情页按钮高亮）。
 * @param sid 歌曲 sid
 */
export function getFavoriteStatus(sid: number): Promise<boolean> {
  return http.get(`/favorite/${sid}/status`) as unknown as Promise<boolean>
}
