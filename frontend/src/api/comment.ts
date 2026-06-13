import http from './http'
import type { CommentCreateDTO, CommentVO, PageResult } from './types'

/**
 * 评论接口。查看类软鉴权（登录带 token 回填 likedByMe）；
 * 发表/点赞/删除需登录。本轮只用主评论（不含回复）。
 */

/**
 * 某歌主评论分页（按时间倒序）。
 * @param sid 歌曲 sid
 * @param page 页码
 * @param size 每页条数
 */
export function listSongComments(
  sid: number,
  page: number,
  size: number,
): Promise<PageResult<CommentVO>> {
  return http.get(`/comment/song/${sid}`, {
    params: { page, size },
  }) as unknown as Promise<PageResult<CommentVO>>
}

/**
 * 发表评论（本轮只发主评论，不传 parentCid）。
 * @param dto sid + content
 * @returns 新评论 cid
 */
export function createComment(dto: CommentCreateDTO): Promise<number> {
  return http.post('/comment', dto) as unknown as Promise<number>
}

/**
 * 点赞评论（幂等）。
 * @param cid 评论 cid
 */
export function likeComment(cid: number): Promise<void> {
  return http.post(`/comment/${cid}/like`) as unknown as Promise<void>
}

/**
 * 取消点赞（幂等）。
 * @param cid 评论 cid
 */
export function unlikeComment(cid: number): Promise<void> {
  return http.delete(`/comment/${cid}/like`) as unknown as Promise<void>
}

/**
 * 删除评论（本人/管理员）。
 * @param cid 评论 cid
 */
export function deleteComment(cid: number): Promise<void> {
  return http.delete(`/comment/${cid}`) as unknown as Promise<void>
}
