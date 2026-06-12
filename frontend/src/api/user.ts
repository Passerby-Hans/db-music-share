import http from './http'
import type { UpdatePasswordDTO, UploadResultVO, UserInfo } from './types'

/**
 * 用户个人中心接口：查资料 / 改昵称 / 改密码 / 传头像。
 * 均需登录（X-Token 由 http 拦截器自动注入）。
 */

/**
 * 获取当前登录用户资料（头像为可展示直链）。
 */
export function getMe(): Promise<UserInfo> {
  return http.get('/user/me') as unknown as Promise<UserInfo>
}

/**
 * 修改昵称（profile 接口不接受头像字段，头像走 uploadAvatar）。
 * @param nickname 新昵称（1~50 字符）
 */
export function updateProfile(nickname: string): Promise<void> {
  return http.put('/user/profile', { nickname }) as unknown as Promise<void>
}

/**
 * 修改密码。成功后后端作废全部会话，前端将在下次请求遇 401 跳登录。
 * @param dto 旧/新密码
 */
export function changePassword(dto: UpdatePasswordDTO): Promise<void> {
  return http.put('/user/password', dto) as unknown as Promise<void>
}

/**
 * 上传/更换头像（multipart）。
 * @param file 图片文件（jpg/jpeg/png/gif，≤5MB，须为真实图片）
 * @returns 含新头像公开直链
 */
export function uploadAvatar(file: File): Promise<UploadResultVO> {
  const form = new FormData()
  form.append('file', file)
  return http.post('/user/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<UploadResultVO>
}
