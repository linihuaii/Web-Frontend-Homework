<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { account, api } from '../api'
import { useLoanReturn } from '../useLoanReturn'
import LoanTable from './LoanTable.vue'

const router = useRouter()
const tab = ref('mine')
const mine = ref([])
const all = ref([])
const loading = ref(true)
const errorMessage = ref('')
const overview = computed(() => [
  { label: '待审批', count: mine.value.filter(loan => loan.status === 'PENDING').length },
  { label: '已借出', count: mine.value.filter(loan => loan.status === 'APPROVED').length },
  { label: '已归还', count: mine.value.filter(loan => loan.status === 'RETURNED').length },
])

function login() {
  return router.replace({ name: 'login', query: { redirect: '/profile' } })
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [mineRows, allRows] = await Promise.all([api('/loans/mine'), api('/loans')])
    mine.value = mineRows
    all.value = allRows
  } catch (error) {
    errorMessage.value = error.message
    if (error.status === 401) await login()
  } finally {
    loading.value = false
  }
}

const { returning, returnBook } = useLoanReturn(load, login)
onMounted(load)
</script>

<template>
  <section class="page-heading">
    <div>
      <h1>个人中心</h1>
      <p>查看账户信息，管理自己的借阅申请和归还记录。</p>
    </div>
    <el-button :loading="loading" :disabled="returning !== null" @click="load">刷新</el-button>
  </section>
  <div class="profile-overview">
    <section v-if="account" class="panel profile-card" aria-labelledby="profile-heading">
      <span class="account-avatar" aria-hidden="true">{{ account.username.charAt(0).toUpperCase() }}</span>
      <div>
        <h2 id="profile-heading">{{ account.username }}</h2>
        <p>读者账号 · 用户编号 {{ account.id }}</p>
      </div>
    </section>
    <dl class="panel profile-stats" aria-label="个人借阅概况" v-loading="loading">
      <div v-for="item in overview" :key="item.label">
        <dt>{{ item.label }}</dt>
        <dd>{{ errorMessage ? '—' : item.count }}</dd>
      </div>
    </dl>
  </div>
  <section class="panel loan-panel" aria-label="借阅记录" v-loading="loading">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <el-tabs v-else v-model="tab">
      <el-tab-pane label="我的借阅" name="mine">
        <LoanTable :rows="mine" :processing="returning" can-return @process="returnBook" />
      </el-tab-pane>
      <el-tab-pane label="全馆借阅动态" name="all"><LoanTable :rows="all" /></el-tab-pane>
    </el-tabs>
  </section>
</template>
