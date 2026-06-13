import http from './http'
import type { UploadResultVO } from './types'

/**
 * 文件上传接口（两步上传第一步）：先传文件拿 object key，再把 key 填入建歌/建专辑。
 * 均需 role≥1（上传者/管理员）。
 */

/**
 * 上传音频到私有桶。
 * @param file 音频文件（mp3/wav/flac/m4a/aac/ogg，≤50MB）
 * @returns key（url 恒为 null，音频不给直链）
 */
export function uploadAudio(file: File): Promise<UploadResultVO> {
  const form = new FormData()
  form.append('file', file)
  return http.post('/file/audio', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<UploadResultVO>
}

/**
 * 上传封面到公开桶。
 * @param file 图片文件（jpg/jpeg/png/gif，≤5MB，须为真实图片）
 * @returns key + 公开直链 url
 */
export function uploadCover(file: File): Promise<UploadResultVO> {
  const form = new FormData()
  form.append('file', file)
  return http.post('/file/cover', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<UploadResultVO>
}
