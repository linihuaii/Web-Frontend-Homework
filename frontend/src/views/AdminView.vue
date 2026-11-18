<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { account, api } from '../api'
import AuthView from './AuthView.vue'
import LoanTable from '../components/LoanTable.vue'

const tab = ref('books')
const books = ref([])
const loans = ref([])
const loading = ref(false)
const saving = ref(false)
const processing = ref(null)
const deleting = ref(false)
const errorMessage = ref('')
const formError = ref('')
const dialog = ref(false)
const editing = ref(null)
const form = reactive({ title: '', author: '', category: '', description: '', total: 1 })
let loadSequence = 0

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const [bookRows, loanRows] = await Promise.all([api('/books'), api('/admin/loans')])
    if (sequence !== loadSequence) return
    books.value = bookRows
    loans.value = loanRows
  } catch (error) {
    if (sequence === loadSequence) errorMessage.value = error.message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

watch(() => account.value?.role, role => {
  if (role === 'ADMIN') {
    load()
  } else {
    loadSequence++
    books.value = []
    loans.value = []
    dialog.value = false
    loading.value = false
  }
}, { immediate: true })

function openBook(book) {
  editing.value = book?.id ?? null
  formError.value = ''
  Object.assign(form, {
    title: book?.title ?? '',
    author: book?.author ?? '',
    category: book?.category ?? '',
    description: book?.description ?? '',
    total: book?.total ?? 1,
  })
  dialog.value = true
}

async function saveBook() {
  if (saving.value) return
  formError.value = ''
  if (![form.title, form.author, form.category].every(value => value.trim()) || !Number.isInteger(form.total)) {
    formError.value = '请填写书名、作者、分类和整数馆藏数量'
    return
  }
  saving.value = true
  try {
    const isEdit = editing.value !== null
    await api(isEdit ? `/admin/books/${editing.value}` : '/admin/books', {
      method: isEdit ? 'PUT' : 'POST', body: { ...form },
    })
    dialog.value = false
    ElMessage.success('图书已保存')
    await load()
  } catch (error) {
    formError.value = error.message
  } finally {
    saving.value = false
  }
}

async function removeBook(book) {
  deleting.value = true
  try {
    await ElMessageBox.confirm(`确定删除《${book.title}》吗？`, '删除图书', {
      type: 'warning', confirmButtonText: '删除',
    })
    await api(`/admin/books/${book.id}`, { method: 'DELETE' })
    ElMessage.success('图书已删除')
    await load()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    deleting.value = false
  }
}

async function processLoan(loan, status) {
  if (processing.value !== null) return
  processing.value = loan.id
  try {
    await api(`/admin/loans/${loan.id}`, { method: 'PATCH', body: status })
    ElMessage.success('借阅状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) await load()
  } finally {
    processing.value = null
  }
}
</script>

<template>
  <AuthView v-if="account?.role !== 'ADMIN'" admin />
  <template v-else>
    <section class="page-heading">
      <div>
        <h1>管理后台</h1>
        <p>维护馆藏信息，处理借阅申请与归还。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </section>
    <section class="panel" v-loading="loading">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
      <el-tabs v-model="tab">
        <el-tab-pane label="图书管理" name="books">
          <div class="admin-actions"><el-button type="primary" @click="openBook()">添加图书</el-button></div>
          <el-table :data="books" row-key="id" empty-text="暂无图书" stripe>
            <el-table-column prop="id" label="编号" width="75" />
            <el-table-column prop="title" label="书名" min-width="175" />
            <el-table-column prop="author" label="作者" min-width="120" />
            <el-table-column prop="category" label="分类" min-width="110" />
            <el-table-column prop="description" label="简介" min-width="150" show-overflow-tooltip />
            <el-table-column prop="total" label="馆藏" width="75" />
            <el-table-column prop="borrowed" label="借出" width="75" />
            <el-table-column prop="available" label="可借" width="75" />
            <el-table-column label="操作" width="155">
              <template #default="{ row }">
                <el-button size="small" @click="openBook(row)">编辑</el-button>
                <el-button size="small" type="danger" plain :disabled="deleting" @click="removeBook(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="借阅管理" name="loans">
          <LoanTable :rows="loans" :processing="processing" admin @process="processLoan" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="dialog" :title="editing === null ? '添加图书' : '编辑图书'" width="min(550px, 92vw)"
      :close-on-click-modal="false" :show-close="!saving" :close-on-press-escape="!saving">
      <el-form label-position="top" @submit.prevent="saveBook">
        <el-form-item label="书名" required><el-input v-model="form.title" maxlength="150" /></el-form-item>
        <el-form-item label="作者" required><el-input v-model="form.author" maxlength="100" /></el-form-item>
        <el-form-item label="分类" required><el-input v-model="form.category" maxlength="60" /></el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="馆藏数量" required>
          <el-input-number v-model="form.total" :min="0" :max="2147483647" :precision="0" />
        </el-form-item>
        <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="form-error" />
        <div class="admin-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存</el-button>
        </div>
      </el-form>
    </el-dialog>
  </template>
</template>
