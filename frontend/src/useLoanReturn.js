import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from './api'

export function useLoanReturn(refresh, onSessionExpired) {
  const returning = ref(null)

  async function returnBook(loan) {
    if (returning.value !== null) return
    returning.value = loan.id
    try {
      await ElMessageBox.confirm(`确认已交还《${loan.title}》？`, '归还图书', {
        confirmButtonText: '确认归还',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await api(`/loans/${loan.id}/return`, { method: 'POST', body: {} })
      ElMessage.success('图书已归还')
      await refresh()
    } catch (error) {
      if (!(error instanceof Error)) return
      ElMessage.error(error.message)
      if (error.status === 401) await onSessionExpired()
      if (error.status === 409 || error.status === 404) await refresh()
    } finally {
      returning.value = null
    }
  }

  return { returning, returnBook }
}
