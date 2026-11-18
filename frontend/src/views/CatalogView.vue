<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { account, api } from '../api'

const router = useRouter()
const books = ref([])
const search = ref('')
const category = ref('')
const loading = ref(false)
const requesting = ref(null)
const selected = ref(null)
const errorMessage = ref('')
const categories = computed(() => [...new Set(books.value.map(book => book.category))])
const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  return books.value.filter(book =>
    (!category.value || book.category === category.value) &&
    [book.title, book.author, book.category].some(field => field.toLowerCase().includes(term))
  )
})

async function loadBooks() {
  loading.value = true
  errorMessage.value = ''
  try {
    books.value = await api('/books')
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function borrow(book) {
  if (account.value?.role !== 'READER') {
    await router.push('/login')
    return
  }
  if (requesting.value !== null) return
  requesting.value = book.id
  try {
    await api('/loans', { method: 'POST', body: { bookId: book.id } })
    ElMessage.success('借阅申请已提交，可在借阅记录中查看进度')
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 401) await router.push('/login')
  } finally {
    requesting.value = null
  }
}

onMounted(loadBooks)
</script>

<template>
  <section class="hero">
    <div>
      <h1>馆藏目录</h1>
      <p>按书名、作者或分类查找图书，在线提交借阅申请。</p>
    </div>
    <div class="shelf" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
  </section>
  <section class="catalog" aria-label="图书列表">
    <div class="toolbar">
      <h2>全部图书 <span>{{ filtered.length }} 种</span></h2>
      <div class="filters">
        <el-input v-model="search" clearable placeholder="搜索书名、作者或分类" aria-label="搜索图书" />
        <el-select v-model="category" clearable placeholder="全部分类" aria-label="图书分类">
          <el-option v-for="name in categories" :key="name" :label="name" :value="name" />
        </el-select>
      </div>
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
        <h3><button type="button" class="book-title" @click="selected = book">{{ book.title }}</button></h3>
        <p class="author">{{ book.author }}</p>
        <p class="description">{{ book.description || '暂无简介' }}</p>
        <div class="book-actions">
          <el-button text @click="selected = book">查看详情</el-button>
          <el-button type="primary" plain :loading="requesting === book.id"
            :disabled="!book.available || (requesting !== null && requesting !== book.id)"
            @click="borrow(book)">申请借阅</el-button>
        </div>
      </article>
    </div>
  </section>
  <el-dialog :model-value="!!selected" :title="selected?.title" width="min(520px, 92vw)"
    @update:model-value="selected = null">
    <template v-if="selected">
      <dl class="book-detail">
        <dt>作者</dt><dd>{{ selected.author }}</dd>
        <dt>分类</dt><dd>{{ selected.category }}</dd>
        <dt>库存</dt><dd>可借 {{ selected.available }} 册，共 {{ selected.total }} 册</dd>
      </dl>
      <p class="detail-description">{{ selected.description || '暂无简介' }}</p>
    </template>
  </el-dialog>
</template>
