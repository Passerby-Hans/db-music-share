/**
 * 后端接口数据类型定义，对齐 `docs/api/*.md` 的响应契约。
 */

/**
 * 统一响应体。后端 HTTP 恒 200，业务结果看 {@link code}。
 * @template T data 字段的载荷类型
 */
export interface ApiResult<T = unknown> {
  /** 业务状态码：200 成功 / 400 / 401 / 403 / 404 / 409 / 500。 */
  code: number
  /** 提示信息。 */
  message: string
  /** 业务数据；纯操作类接口为 null。 */
  data: T
}

/** 登录成功返回体（POST /api/auth/login 的 data）。 */
export interface LoginVO {
  /** 会话令牌，后续放入 X-Token 头。 */
  token: string
  uid: number
  username: string
  nickname: string
  /** 角色：0 普通 / 1 上传者 / 2 管理员。 */
  role: number
}

/** 当前用户资料（GET /api/user/me 的 data）。 */
export interface UserInfo {
  uid: number
  username: string
  nickname: string
  email: string | null
  /** 头像可展示直链（后端已 publicUrl 包装）。 */
  avatar: string | null
  role: number
  regTime: string
}

/** 注册请求参数。 */
export interface RegisterDTO {
  username: string
  password: string
  nickname: string
  email?: string
}

/** 登录请求参数。 */
export interface LoginDTO {
  username: string
  password: string
}

/** 改密请求参数。 */
export interface UpdatePasswordDTO {
  oldPassword: string
  newPassword: string
}

/** 文件上传返回体（含 key 与公开直链）。 */
export interface UploadResultVO {
  key: string | null
  url: string | null
}

/**
 * 分页结果通用包装，对齐后端 PageVO（records/total/page/size）。
 * @template T 列表项类型
 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 歌曲列表项（GET /api/song/public 的 records 项）。cover 已为公开直链。 */
export interface SongVO {
  sid: number
  title: string
  /** 封面公开直链（后端已 publicUrl 包装，可能为 null）。 */
  cover: string | null
  /** 时长（秒），可空。 */
  duration: number | null
  playCount: number
  albumAid: number
  uploaderUid: number
  /** 审核状态：0 待审 / 1 通过 / 2 驳回。 */
  auditStatus: number
  auditRemark: string | null
}

/** 歌曲详情（GET /api/song/public/{sid}），比列表项多歌词/音频/上传时间。 */
export interface SongDetailVO extends SongVO {
  lyric: string | null
  audioPath: string | null
  createTime: string
}

/** 专辑视图（列表/详情的 album）。 */
export interface AlbumVO {
  aid: number
  albumName: string
  cover: string | null
  releaseDate: string | null
  introduction: string | null
  /** 是否系统托管的缺省专辑（true 则禁改禁删）。 */
  isDefault: boolean
  creatorUid: number
}

/** 上传歌曲请求体。专辑归属三选一：albumAid / newAlbumName / 都不传(缺省专辑)。 */
export interface SongUploadDTO {
  title: string
  audioPath: string
  cover?: string
  duration?: number
  lyric?: string
  albumAid?: number
  newAlbumName?: string
}

/** 修改歌曲元信息请求体（不含专辑归属与审核态）。 */
export interface SongUpdateDTO {
  title: string
  cover?: string
  duration?: number
  lyric?: string
}

/** 新建/修改专辑请求体。 */
export interface AlbumDTO {
  albumName: string
  cover?: string
  releaseDate?: string
  introduction?: string
}

/** 专辑详情（含其下可见歌曲）。 */
export interface AlbumDetailVO {
  album: AlbumVO
  songs: SongVO[]
}

/** 歌曲审核请求体。pass=true 通过；false 驳回（remark 必填）。 */
export interface SongAuditDTO {
  pass: boolean
  remark?: string
}

/** 管理后台用户视图（比 UserInfo 多 status）。 */
export interface AdminUserVO {
  uid: number
  username: string
  nickname: string
  email: string | null
  avatar: string | null
  role: number
  /** 账号状态：0 正常 / 1 封禁。 */
  status: number
  regTime: string
}

/** 孤儿扫描结果。 */
export interface OrphanScanVO {
  dryRun: boolean
  safeMinutes: number
  audioScanned: number
  coverScanned: number
  orphanCount: number
  deletedCount: number
  /** 孤儿清单，格式 桶类型:objectKey（AUDIO/COVER）。 */
  orphanKeys: string[]
}

/** 评论项（主评论）。 */
export interface CommentVO {
  cid: number
  uid: number
  nickname: string | null
  avatar: string | null
  content: string
  likeCount: number
  likedByMe: boolean
  replyCount: number
  createTime: string
}

/** 发表评论请求体。parentCid 传则为回复（本轮只发主评论，不传）。 */
export interface CommentCreateDTO {
  sid: number
  content: string
  parentCid?: number
}

/** 歌曲评分概况。 */
export interface RatingSummaryVO {
  sid: number
  avgScore: number
  ratingCount: number
  /** 我的评分；游客或未评过为 null。 */
  myScore: number | null
}

/** 角色码常量。 */
export const Role = {
  NORMAL: 0,
  UPLOADER: 1,
  ADMIN: 2,
} as const

/** 账号状态常量。 */
export const UserStatus = {
  NORMAL: 0,
  BANNED: 1,
} as const

/** 审核状态常量。 */
export const AuditStatus = {
  PENDING: 0,
  PASSED: 1,
  REJECTED: 2,
} as const
