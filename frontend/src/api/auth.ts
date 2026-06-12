import http from './http'
import type { LoginDTO, LoginVO, RegisterDTO } from './types'

/**
 * 鉴权相关接口：注册 / 登录 / 登出。
 *
 * 说明：http 响应拦截器已把后端 `{code,message,data}` 解包为 `data`，
 * 故这里的返回类型直接标注为 data 的类型；类型断言用于覆盖 axios 默认签名。
 */

/**
 * 注册新用户。
 * @param dto 注册参数
 * @returns 新用户 uid
 */
export function register(dto: RegisterDTO): Promise<number> {
  return http.post('/auth/register', dto) as unknown as Promise<number>
}

/**
 * 登录。
 * @param dto 登录参数
 * @returns 令牌与用户信息
 */
export function login(dto: LoginDTO): Promise<LoginVO> {
  return http.post('/auth/login', dto) as unknown as Promise<LoginVO>
}

/**
 * 登出（使当前令牌即时失效）。
 */
export function logout(): Promise<void> {
  return http.post('/auth/logout') as unknown as Promise<void>
}
