import http from './http'
import type { AlbumDTO, AlbumDetailVO, AlbumVO, PageResult } from './types'

/**
 * 专辑接口。查询类登录即可；建/改/删需 role≥1 且本人创建（管理员越过）。
 */

/**
 * 我创建的专辑（含为单曲自动生成的缺省专辑），分页。
 * @param page 页码
 * @param size 每页条数
 */
export function listMyAlbums(page: number, size: number): Promise<PageResult<AlbumVO>> {
  return http.get('/album/mine', { params: { page, size } }) as unknown as Promise<PageResult<AlbumVO>>
}

/**
 * 公开专辑详情 + 其下可见歌曲。
 * @param aid 专辑 aid
 */
export function getAlbumDetail(aid: number): Promise<AlbumDetailVO> {
  return http.get(`/album/public/${aid}`) as unknown as Promise<AlbumDetailVO>
}

/**
 * 新建普通专辑。
 * @param dto 专辑信息
 * @returns 新专辑 aid
 */
export function createAlbum(dto: AlbumDTO): Promise<number> {
  return http.post('/album', dto) as unknown as Promise<number>
}

/**
 * 修改专辑（本人/管理员；缺省专辑禁改，后端返回 409）。
 * @param aid 专辑 aid
 * @param dto 新信息
 */
export function updateAlbum(aid: number, dto: AlbumDTO): Promise<void> {
  return http.put(`/album/${aid}`, dto) as unknown as Promise<void>
}

/**
 * 删除专辑（本人/管理员；级联软删其下歌曲）。
 * @param aid 专辑 aid
 */
export function deleteAlbum(aid: number): Promise<void> {
  return http.delete(`/album/${aid}`) as unknown as Promise<void>
}
