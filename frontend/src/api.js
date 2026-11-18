import { ref } from 'vue'

export const account = ref(null)

export async function api(path, { method = 'GET', body } = {}) {
  const response = await fetch('/api' + path, {
    method,
    credentials: 'same-origin',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const payload = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : null
  if (!response.ok) {
    if (response.status === 401 && !['/auth/login', '/auth/admin-login'].includes(path)) account.value = null
    const error = new Error(payload?.message || `请求失败（${response.status}）`)
    error.status = response.status
    throw error
  }
  return payload
}

export async function restoreAccount() {
  try {
    account.value = await api('/auth/me')
  } catch (error) {
    if (error.status !== 401) throw error
  }
}
