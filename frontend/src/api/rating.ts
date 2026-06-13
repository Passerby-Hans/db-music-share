import http from './http'
import type { RatingSummaryVO } from './types'

/**
 * 评分接口。概况软鉴权（带 token 回填 myScore）；提交/撤销需登录。
 */

/**
 * 某歌评分概况（平均分、人数、我的评分）。
 * @param sid 歌曲 sid
 */
export function getRatingSummary(sid: number): Promise<RatingSummaryVO> {
  return http.get(`/rating/${sid}`) as unknown as Promise<RatingSummaryVO>
}

/**
 * 提交/更新评分（upsert：首评新增，再评改分）。
 * @param sid 歌曲 sid
 * @param score 1~5
 */
export function submitRating(sid: number, score: number): Promise<void> {
  return http.post(`/rating/${sid}`, { score }) as unknown as Promise<void>
}

/**
 * 撤销我的评分（幂等）。
 * @param sid 歌曲 sid
 */
export function removeRating(sid: number): Promise<void> {
  return http.delete(`/rating/${sid}`) as unknown as Promise<void>
}
