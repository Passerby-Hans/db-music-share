import { defineStore } from 'pinia'
import { TOKEN_KEY } from '@/api/http'
import * as authApi from '@/api/auth'
import type { LoginDTO, LoginVO, UserInfo } from '@/api/types'
import { Role } from '@/api/types'

/** 持久化用户信息的 localStorage key（token 的 key 见 api/http 的 TOKEN_KEY）。 */
const USER_KEY = 'music_user'

/** 从 localStorage 读取已存的用户信息（刷新页面后恢复登录态）。 */
function loadUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as UserInfo) : null
}

/**
 * 鉴权状态仓库：集中管理 token 与当前用户信息，并持久化到 localStorage，
 * 使刷新页面后登录态不丢。token 同时写入 {@link TOKEN_KEY}，供 http 拦截器读取。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    /** 会话令牌；空串表示未登录。 */
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    /** 当前用户信息；未登录为 null。 */
    user: loadUser() as UserInfo | null,
  }),

  getters: {
    /** 是否已登录。 */
    isLoggedIn: (state): boolean => !!state.token,
    /** 是否管理员（role=2）。 */
    isAdmin: (state): boolean => state.user?.role === Role.ADMIN,
    /** 当前用户角色（未登录返回 null）。 */
    role: (state): number | null => state.user?.role ?? null,
  },

  actions: {
    /**
     * 登录：调接口拿 token + 用户信息，写入 state 与 localStorage。
     * @param dto 登录参数
     */
    async login(dto: LoginDTO): Promise<void> {
      const vo: LoginVO = await authApi.login(dto)
      this.token = vo.token
      this.user = {
        uid: vo.uid,
        username: vo.username,
        nickname: vo.nickname,
        email: null,
        avatar: null,
        role: vo.role,
        regTime: '',
      }
      localStorage.setItem(TOKEN_KEY, vo.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },

    /**
     * 用最新的用户资料覆盖 store（如个人中心拉取 /me 后同步昵称/头像）。
     * @param info 最新用户资料
     */
    setUser(info: UserInfo): void {
      this.user = info
      localStorage.setItem(USER_KEY, JSON.stringify(info))
    },

    /**
     * 登出：尝试调后端作废会话（失败也无妨），清本地登录态。
     */
    async logout(): Promise<void> {
      try {
        await authApi.logout()
      } catch {
        // 后端登出失败（如已过期）不阻断本地清理
      }
      this.clear()
    },

    /**
     * 仅清本地登录态（供 401 拦截直接调用，不再打后端）。
     */
    clear(): void {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
