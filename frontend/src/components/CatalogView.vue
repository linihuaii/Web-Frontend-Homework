<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { account, api } from '../api'
import { useLoanReturn } from '../useLoanReturn'

const router = useRouter()
const books = ref([])
const loans = ref([])
const search = ref('')
const category = ref('')
const onlyAvailable = ref(false)
const loading = ref(true)
const requesting = ref(null)
const selectedId = ref(null)
const errorMessage = ref('')
const loanError = ref('')
const selected = computed(() => books.value.find(book => book.id === selectedId.value))
const activeLoans = computed(() => new Map(loans.value
  .filter(loan => ['PENDING', 'APPROVED'].includes(loan.status))
  .map(loan => [loan.bookId, loan])))
const categories = computed(() => [...new Set(books.value.map(book => book.category))].sort((a, b) => a.localeCompare(b, 'zh-CN')))
const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  return books.value.filter(book =>
    (!category.value || book.category === category.value) &&
    (!onlyAvailable.value || book.available > 0) &&
    [book.title, book.author, book.category].some(field => field.toLowerCase().includes(term))
  )
})

async function loadLoans() {
  loanError.value = ''
  const reader = account.value
  if (reader?.role !== 'READER') {
    loans.value = []
    return
  }
  try {
    const rows = await api('/loans/mine')
    if (reader === account.value) loans.value = rows
  } catch (error) {
    loans.value = []
    if (error.status !== 401) loanError.value = error.message
  }
}

async function loadBooks() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [rows] = await Promise.all([api('/books'), loadLoans()])
    books.value = rows
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function login() {
  return router.push({ name: 'login', query: { redirect: '/books' } })
}

const { returning, returnBook } = useLoanReturn(loadBooks, login)
const busy = computed(() => requesting.value !== null || returning.value !== null)

async function borrow(book) {
  if (account.value?.role !== 'READER') {
    await login()
    return
  }
  if (busy.value) return
  requesting.value = book.id
  try {
    await api('/loans', { method: 'POST', body: { bookId: book.id } })
    ElMessage.success('借阅申请已提交，可在个人中心查看进度')
    await loadLoans()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 401) await login()
    if (error.status === 409 || error.status === 404) await loadBooks()
  } finally {
    requesting.value = null
  }
}

onMounted(loadBooks)
</script>

<template>
  <section class="hero">
    <div>
      <h1>书籍管理</h1>
      <p>查询馆藏与库存，申请借阅或登记归还。</p>
    </div>
    <div class="shelf" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
  </section>
  <section class="catalog" aria-label="图书列表">
    <div class="toolbar">
      <h2>馆藏图书 <span v-if="!loading && !errorMessage">{{ filtered.length }} / {{ books.length }} 种</span></h2>
      <div class="filters">
        <el-input v-model="search" clearable placeholder="搜索书名、作者或分类" aria-label="搜索图书" />
        <el-select v-model="category" clearable placeholder="全部分类" aria-label="图书分类">
          <el-option v-for="name in categories" :key="name" :label="name" :value="name" />
        </el-select>
        <el-checkbox v-model="onlyAvailable">仅看有库存</el-checkbox>
        <el-button :loading="loading" :disabled="busy" @click="loadBooks">刷新</el-button>
      </div>
    </div>
    <div v-if="loanError && !errorMessage && !loading" class="error-panel loan-state-error">
      <el-alert :title="'无法加载个人借阅状态。' + loanError" type="error" :closable="false" />
      <el-button @click="loadBooks">重试</el-button>
    </div>
    <div v-if="errorMessage" class="error-panel">
      <el-alert :title="errorMessage" type="error" :closable="false" />
      <el-button @click="loadBooks">重试</el-button>
    </div>
    <el-skeleton v-else-if="loading" :rows="5" animated />
    <el-empty v-else-if="!filtered.length" :description="books.length ? '没有匹配的图书' : '馆藏目录暂无图书'" />
    <div v-else class="book-grid">
      <article v-for="book in filtered" :key="book.id" class="book-card">
        <div class="book-top">
          <span class="category">{{ book.category }}</span>
          <span class="stock" :class="{ unavailable: !book.available }">可借 {{ book.available }} / {{ book.total }}</span>
        </div>
        <h3><button type="button" class="book-title" @click="selectedId = book.id">{{ book.title }}</button></h3>
        <p class="author">{{ book.author }}</p>
        <p class="description">{{ book.description || '暂无简介' }}</p>
        <div class="book-actions">
          <el-button text @click="selectedId = book.id">查看详情</el-button>
          <el-button v-if="account?.role === 'READER' && activeLoans.get(book.id)?.status === 'APPROVED'"
            type="primary" :disabled="busy" :loading="returning === activeLoans.get(book.id).id"
            @click="returnBook(activeLoans.get(book.id))">归还图书</el-button>
          <el-tag v-else-if="account?.role === 'READER' && activeLoans.get(book.id)?.status === 'PENDING'"
            type="warning">待审批</el-tag>
          <el-button v-else-if="account?.role !== 'ADMIN'" type="primary" plain :loading="requesting === book.id"
            :disabled="!book.available || busy || !!loanError"
            @click="borrow(book)">申请借阅</el-button>
        </div>
      </article>
    </div>
  </section>
  <el-dialog :model-value="!!selected" :title="selected?.title" width="min(520px, 92vw)"
    @update:model-value="selectedId = null">
    <template v-if="selected">
      <dl class="book-detail">
        <dt>作者</dt><dd>{{ selected.author }}</dd>
        <dt>分类</dt><dd>{{ selected.category }}</dd>
        <dt>库存</dt><dd>可借 {{ selected.available }} 册，共 {{ selected.total }} 册</dd>
      </dl>
      <p class="detail-description">{{ selected.description || '暂无简介' }}</p>
    </template>
    <template #footer><el-button @click="selectedId = null">关闭</el-button></template>
  </el-dialog>
</template>
