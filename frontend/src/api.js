import axios from 'axios'
import { ref } from 'vue'

export const account = ref(null)
const client = axios.create({ baseURL: '/api', withCredentials: true })

export async function api(path, { method = 'GET', body } = {}) {
  const currentAccount = account.value
  try {
    const response = await client.request({
      url: path,
      method,
      data: body,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    })
    return response.data === '' ? null : response.data
  } catch (cause) {
    const { status, data } = cause.response ?? {}
    if (status === 401 && currentAccount === account.value && !['/auth/login', '/auth/admin-login'].includes(path)) {
      account.value = null
    }
    const error = new Error(data?.message || (status ? `请求失败（${status}）` : '无法连接服务器，请稍后重试'), { cause })
    error.status = status
    throw error
  }
}

export async function restoreAccount() {
  try {
    account.value = await api('/auth/me')
  } catch (error) {
    if (error.status !== 401) throw error
  }
}
