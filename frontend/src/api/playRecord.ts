import http from './http'

/**
 * 点唱记录接口（2.0 第①块）。播放时上报一次，后端事务内 `play_count+1` + Redis 三榜 ZINCRBY，
 * 同 uid+sid 60s 去重（防刷量）。登录即可（无 @RequireRole、非白名单，读会话取 uid）。
 */

/**
 * 上报一次点唱（播放埋点）。建议 fire-and-forget：不阻断播放、失败由 http 拦截器处理。
 * @param sid 歌曲 sid
 */
export function recordPlay(sid: number): Promise<void> {
  return http.post(`/play-record/${sid}`) as unknown as Promise<void>
}
