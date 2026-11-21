<script setup>
import { computed, onMounted, ref } from 'vue'
import { account, api } from '../api'

const stats = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const formatter = new Intl.NumberFormat('zh-CN')
const inventory = [
  { key: 'bookTitles', label: '图书品种', unit: '种' },
  { key: 'totalCopies', label: '馆藏总量', unit: '册' },
  { key: 'availableCopies', label: '可借库存', unit: '册' },
  { key: 'borrowedCopies', label: '当前借出', unit: '册' },
]
const readers = [
  { key: 'readers', label: '注册读者', unit: '人' },
  { key: 'activeReaders', label: '当前持有借出图书', unit: '人' },
  { key: 'pendingLoans', label: '待审批申请', unit: '笔' },
]
const borrowedPercent = computed(() => stats.value?.totalCopies
  ? Math.round(stats.value.borrowedCopies / stats.value.totalCopies * 100)
  : 0)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    stats.value = await api('/stats')
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="hero home-hero">
    <div>
      <h1>图书管理系统</h1>
      <p>探索馆藏，在线申请借阅，随时查看借阅进度。</p>
      <div class="home-actions">
        <RouterLink class="primary-link" to="/books">浏览馆藏</RouterLink>
        <RouterLink v-if="account?.role === 'READER'" class="hero-link" to="/profile">个人中心</RouterLink>
        <RouterLink v-else-if="account?.role === 'ADMIN'" class="hero-link" to="/admin">进入管理后台</RouterLink>
        <RouterLink v-else-if="!account" class="hero-link" to="/register">注册读者账号</RouterLink>
      </div>
    </div>
    <div class="shelf" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
  </section>

  <section class="info-section" aria-labelledby="overview">
    <div class="toolbar dashboard-toolbar">
      <h2 id="overview">图书馆概况</h2>
      <el-button :loading="loading" @click="load">刷新统计</el-button>
    </div>
    <div v-if="errorMessage" class="error-panel">
      <el-alert :title="errorMessage" type="error" :closable="false" />
    </div>
    <div v-else class="dashboard-grid" v-loading="loading">
      <section class="panel dashboard-card" aria-labelledby="inventory">
        <h3 id="inventory">馆藏统计</h3>
        <dl class="stat-list">
          <div v-for="metric in inventory" :key="metric.key">
            <dt>{{ metric.label }}</dt>
            <dd>{{ stats ? formatter.format(stats[metric.key]) : '—' }} <small>{{ metric.unit }}</small></dd>
          </div>
        </dl>
        <div v-if="stats" class="inventory-meter">
          <label for="borrowed-percent">借出比例 <strong>{{ borrowedPercent }}%</strong></label>
          <meter id="borrowed-percent" min="0" max="100" :value="borrowedPercent">{{ borrowedPercent }}%</meter>
        </div>
      </section>
      <section class="panel dashboard-card" aria-labelledby="readers">
        <h3 id="readers">读者与借阅统计</h3>
        <dl class="stat-list">
          <div v-for="metric in readers" :key="metric.key">
            <dt>{{ metric.label }}</dt>
            <dd>{{ stats ? formatter.format(stats[metric.key]) : '—' }} <small>{{ metric.unit }}</small></dd>
          </div>
        </dl>
      </section>
    </div>
  </section>

  <section class="info-section" aria-labelledby="services">
    <h2 id="services">读者服务</h2>
    <div class="info-grid">
      <article class="panel info-card">
        <h3>查找图书</h3>
        <p>按书名、作者或分类查找图书，查看简介和当前可借册数。</p>
        <RouterLink to="/books">进入书籍管理</RouterLink>
      </article>
      <article class="panel info-card">
        <h3>管理借阅</h3>
        <p>查看个人申请状态，在归还图书后更新借阅记录。</p>
        <RouterLink v-if="account?.role === 'ADMIN'" to="/admin">打开管理后台</RouterLink>
        <RouterLink v-else to="/profile">打开个人中心</RouterLink>
      </article>
      <article class="panel info-card">
        <h3>获取帮助</h3>
        <p>查阅常见问题，或向图书管理员提交反馈。</p>
        <RouterLink to="/contact">联系我们</RouterLink>
      </article>
    </div>
  </section>
</template>
