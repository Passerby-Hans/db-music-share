import http from './http'
import type {
  AdminUserVO,
  OrphanScanVO,
  PageResult,
  SongAuditDTO,
  SongVO,
  TopUploaderVO,
  TopUserVO,
} from './types'

/**
 * 管理后台接口（均需 role=2）。涵盖歌曲审核、用户管理、存储维护。
 */

// ============================ 歌曲审核 ============================

/**
 * 待审歌曲列表（auditStatus=0 且未删），分页。
 * @param page 页码
 * @param size 每页条数
 */
export function listPendingSongs(page: number, size: number): Promise<PageResult<SongVO>> {
  return http.get('/admin/song/pending', {
    params: { page, size },
  }) as unknown as Promise<PageResult<SongVO>>
}

/**
 * 审核一首待审歌曲：通过或驳回。
 * @param sid 歌曲 sid
 * @param dto pass=true 通过；false 驳回（remark 必填）
 */
export function auditSong(sid: number, dto: SongAuditDTO): Promise<void> {
  return http.post(`/admin/song/${sid}/audit`, dto) as unknown as Promise<void>
}

// ============================ 用户管理 ============================

/**
 * 用户列表，支持关键字（用户名/昵称）与角色/状态筛选，分页。
 * @param params 查询参数（均可选）
 */
export function listUsers(params: {
  keyword?: string
  role?: number
  status?: number
  page: number
  size: number
}): Promise<PageResult<AdminUserVO>> {
  return http.get('/admin/user', { params }) as unknown as Promise<PageResult<AdminUserVO>>
}

/**
 * 封禁用户（即时踢下线）。
 * @param uid 目标用户 uid
 */
export function banUser(uid: number): Promise<void> {
  return http.put(`/admin/user/${uid}/ban`) as unknown as Promise<void>
}

/**
 * 解封用户。
 * @param uid 目标用户 uid
 */
export function unbanUser(uid: number): Promise<void> {
  return http.put(`/admin/user/${uid}/unban`) as unknown as Promise<void>
}

/**
 * 修改用户角色（即时作废其会话）。
 * @param uid 目标用户 uid
 * @param role 新角色 0/1/2
 */
export function changeUserRole(uid: number, role: number): Promise<void> {
  return http.put(`/admin/user/${uid}/role`, { role }) as unknown as Promise<void>
}

// ============================ 存储维护 ============================

/**
 * 孤儿文件扫描/清理。
 * @param dryRun true 只扫描列出（演练）；false 实际删除
 */
export function orphanScan(dryRun: boolean): Promise<OrphanScanVO> {
  return http.post('/admin/storage/orphan-scan', null, {
    params: { dryRun },
  }) as unknown as Promise<OrphanScanVO>
}

// ============================ 统计报表 ============================

/**
 * 用户活跃度 TOP10（按点唱次数倒序，role=2）。
 */
export function getTopUsers(): Promise<TopUserVO[]> {
  return http.get('/admin/stats/top-users') as unknown as Promise<TopUserVO[]>
}

/**
 * 上传者贡献 TOP10（按总播放量倒序，role=2）。
 */
export function getTopUploaders(): Promise<TopUploaderVO[]> {
  return http.get('/admin/stats/top-uploaders') as unknown as Promise<TopUploaderVO[]>
}
