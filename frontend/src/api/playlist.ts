import http from './http'
import type { PageResult, PlaylistDTO, PlaylistDetailVO, PlaylistVO } from './types'

/**
 * 歌单接口。建/看自己歌单登录即可；改/删/加歌/移歌需 owner 或管理员。
 * 歌单详情软鉴权（登录可见自己的私密歌单）。
 */

/**
 * 创建歌单。
 * @param dto 歌单信息（isPublic 缺省 true）
 * @returns 新歌单 plid
 */
export function createPlaylist(dto: PlaylistDTO): Promise<number> {
  return http.post('/playlist', dto) as unknown as Promise<number>
}

/**
 * 修改歌单元信息（owner/管理员）。
 * @param plid 歌单 plid
 * @param dto 新信息（isPublic 必填）
 */
export function updatePlaylist(plid: number, dto: PlaylistDTO): Promise<void> {
  return http.put(`/playlist/${plid}`, dto) as unknown as Promise<void>
}

/**
 * 删除歌单（owner/管理员；曲目级联删）。
 * @param plid 歌单 plid
 */
export function deletePlaylist(plid: number): Promise<void> {
  return http.delete(`/playlist/${plid}`) as unknown as Promise<void>
}

/**
 * 我的歌单分页（含私密），按创建时间倒序。
 * @param page 页码
 * @param size 每页条数
 */
export function listMyPlaylists(page: number, size: number): Promise<PageResult<PlaylistVO>> {
  return http.get('/playlist/mine', {
    params: { page, size },
  }) as unknown as Promise<PageResult<PlaylistVO>>
}

/**
 * 歌单详情 + 曲目分页（软鉴权）。
 * @param plid 歌单 plid
 * @param page 曲目页码
 * @param size 曲目每页条数
 */
export function getPlaylistDetail(
  plid: number,
  page: number,
  size: number,
): Promise<PlaylistDetailVO> {
  return http.get(`/playlist/${plid}`, {
    params: { page, size },
  }) as unknown as Promise<PlaylistDetailVO>
}

/**
 * 向歌单加歌（owner/管理员，幂等，歌须公开可见）。
 * @param plid 歌单 plid
 * @param sid 歌曲 sid
 */
export function addSongToPlaylist(plid: number, sid: number): Promise<void> {
  return http.post(`/playlist/${plid}/songs/${sid}`) as unknown as Promise<void>
}

/**
 * 从歌单移歌（owner/管理员，幂等）。
 * @param plid 歌单 plid
 * @param sid 歌曲 sid
 */
export function removeSongFromPlaylist(plid: number, sid: number): Promise<void> {
  return http.delete(`/playlist/${plid}/songs/${sid}`) as unknown as Promise<void>
}
