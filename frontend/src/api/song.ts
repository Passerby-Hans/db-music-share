import http from './http'
import type { PageResult, SongDetailVO, SongVO } from './types'

/**
 * 歌曲查询接口（公开口径A：已审核 + 未删）。均需登录。
 * http 响应拦截器已解包 data，故返回类型直接标注 data 类型。
 */

/**
 * 公开歌曲列表，可按标题模糊搜索，按 sid 倒序分页。
 * @param keyword 标题关键词，可空
 * @param page 页码（从 1 起）
 * @param size 每页条数
 */
export function listPublicSongs(
  keyword: string,
  page: number,
  size: number,
): Promise<PageResult<SongVO>> {
  return http.get('/song/public', {
    params: { keyword: keyword || undefined, page, size },
  }) as unknown as Promise<PageResult<SongVO>>
}

/**
 * 公开歌曲详情（含歌词等）。
 * @param sid 歌曲 sid
 */
export function getSongDetail(sid: number): Promise<SongDetailVO> {
  return http.get(`/song/public/${sid}`) as unknown as Promise<SongDetailVO>
}

/**
 * 取歌曲音频播放地址（限时预签名 URL）。
 * 前端 <audio> 直接用该 URL，浏览器原生支持 HTTP Range「边放边加载」。
 * @param sid 歌曲 sid
 * @returns 预签名播放 URL
 */
export function getPlayUrl(sid: number): Promise<string> {
  return http.get(`/song/public/${sid}/play-url`) as unknown as Promise<string>
}
