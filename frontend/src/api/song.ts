import http from './http'
import type { PageResult, SongDetailVO, SongUpdateDTO, SongUploadDTO, SongVO } from './types'

/**
 * 歌曲查询接口（公开口径A：已审核 + 未删）。均需登录。
 * http 响应拦截器已解包 data，故返回类型直接标注 data 类型。
 */

/**
 * 歌曲公开列表排序维度（与后端 GET /song/public 的 sort 取值对齐）。
 * - `create_time`：最新（按创建时间倒序）
 * - `play_count`：最热（按播放量倒序）
 * 不传或传未知值 → 后端回默认（sid 倒序）。后端 desc 固定，无升降序。
 */
export type SongSort = 'create_time' | 'play_count'

/**
 * 公开歌曲列表，可按标题模糊搜索 + 指定排序，分页。
 * @param keyword 标题关键词，可空
 * @param page 页码（从 1 起）
 * @param size 每页条数
 * @param sort 排序维度，可空（不传走默认 sid 倒序）；后端 desc 固定
 */
export function listPublicSongs(
  keyword: string,
  page: number,
  size: number,
  sort?: SongSort,
): Promise<PageResult<SongVO>> {
  return http.get('/song/public', {
    params: { keyword: keyword || undefined, page, size, sort },
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

// ============================ 上传者工作台 ============================

/**
 * 上传歌曲（建歌，待审）。专辑归属三选一见 {@link SongUploadDTO}。
 * @param dto 建歌参数（audioPath 为先传文件拿到的 key）
 * @returns 新歌曲 sid
 */
export function uploadSong(dto: SongUploadDTO): Promise<number> {
  return http.post('/song', dto) as unknown as Promise<number>
}

/**
 * 我的上传（口径B：本人 + 未删，任意审核态），分页。
 * @param page 页码
 * @param size 每页条数
 */
export function listMySongs(page: number, size: number): Promise<PageResult<SongVO>> {
  return http.get('/song/mine', { params: { page, size } }) as unknown as Promise<PageResult<SongVO>>
}

/**
 * 修改歌曲元信息（本人/管理员）。上传者改后审核态回退待审。
 * @param sid 歌曲 sid
 * @param dto 新元信息
 */
export function updateSong(sid: number, dto: SongUpdateDTO): Promise<void> {
  return http.put(`/song/${sid}`, dto) as unknown as Promise<void>
}

/**
 * 移动歌曲到另一专辑（本人/管理员）。
 * @param sid 歌曲 sid
 * @param targetAlbumAid 目标专辑 aid
 */
export function moveSong(sid: number, targetAlbumAid: number): Promise<void> {
  return http.put(`/song/${sid}/album`, { targetAlbumAid }) as unknown as Promise<void>
}

/**
 * 软删除歌曲（本人/管理员）。删除即物理删其音频与封面文件。
 * @param sid 歌曲 sid
 */
export function deleteSong(sid: number): Promise<void> {
  return http.delete(`/song/${sid}`) as unknown as Promise<void>
}
