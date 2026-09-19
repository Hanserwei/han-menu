export interface StoredSession {
  accessToken: string
  expiresAt: string
}
export const SESSION_KEY = 'han-menu.staff-session.v1'

/** 只存员工不透明令牌及到期时刻，不相信存储中的身份或角色。 */
export function parseSession(value: string | null, now = Date.now()): StoredSession | null {
  try {
    const parsed: unknown = JSON.parse(value || 'null')
    if (!parsed || typeof parsed !== 'object') return null
    const record = parsed as Record<string, unknown>
    if (
      typeof record.accessToken !== 'string' ||
      !/^hme_[A-Za-z0-9_-]{43}$/.test(record.accessToken) ||
      typeof record.expiresAt !== 'string' ||
      !(Date.parse(record.expiresAt) > now)
    )
      return null
    return { accessToken: record.accessToken, expiresAt: record.expiresAt }
  } catch {
    return null
  }
}

/** 浏览器禁用存储时仍允许当前内存会话，刷新后重新登录。 */
export function readSession(): StoredSession | null {
  try {
    return parseSession(sessionStorage.getItem(SESSION_KEY))
  } catch {
    return null
  }
}
export function writeSession(session: StoredSession | null) {
  try {
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else sessionStorage.removeItem(SESSION_KEY)
  } catch {
    /* 隐私模式退回内存会话，不扩大存储范围。 */
  }
}
