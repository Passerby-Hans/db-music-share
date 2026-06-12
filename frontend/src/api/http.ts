import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from './types'

/**
 * 全局 token 存储 key（与 auth store 持久化保持一致）。
 * http 拦截器直接读 localStorage 而非 import store，避免「store ↔ api」循环依赖。
 */
export const TOKEN_KEY = 'music_token'

/** 会话令牌请求头名（与后端 AuthInterceptor.TOKEN_HEADER 一致）。 */
const TOKEN_HEADER = 'X-Token'

/**
 * 401 处理钩子。由 router 层注入：清登录态并跳登录页。
 * 这样 http 层不直接依赖 router/store，保持单向依赖。
 */
let onUnauthorized: (() => void) | null = null

/** 注册 401 回调（在 main/router 初始化时调用一次）。 */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

/**
 * axios 实例：baseURL=/api（由 Vite 代理转发到后端），统一超时与 JSON 头。
 */
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

/** 请求拦截器：自动注入 X-Token（若已登录）。 */
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set(TOKEN_HEADER, token)
  }
  return config
})

/**
 * 响应拦截器：统一解包后端 `{code,message,data}`。
 * - code===200：返回 data（调用方直接拿业务数据）；
 * - 401：触发登出钩子（跳登录），并抛错；
 * - 其它非 200：弹消息并抛错。
 * 网络层异常（无响应）也兜底提示。
 */
http.interceptors.response.use(
  // 刻意解包：把 {code,message,data} 直接还原为 data 返回，故返回类型偏离 AxiosResponse，
  // 用 any 适配 axios 拦截器签名（调用方在 api 层用泛型断言拿到正确类型）。
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (response): any => {
    const body = response.data as ApiResult
    if (body.code === 200) {
      return body.data
    }
    if (body.code === 401) {
      onUnauthorized?.()
    }
    // 403/404/409/500/400 等：统一弹后端给的 message
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || `业务错误 ${body.code}`))
  },
  (error) => {
    // 网络错误、超时、5xx 等无标准响应体的情况
    const msg = error?.response?.status
      ? `请求失败（HTTP ${error.response.status}）`
      : '网络异常，请检查连接'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default http
