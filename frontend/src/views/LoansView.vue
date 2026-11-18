<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import LoanTable from '../components/LoanTable.vue'

const router = useRouter()
const tab = ref('mine')
const mine = ref([])
const all = ref([])
const loading = ref(false)
const errorMessage = ref('')

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [mineRows, allRows] = await Promise.all([api('/loans/mine'), api('/loans')])
    mine.value = mineRows
    all.value = allRows
  } catch (error) {
    errorMessage.value = error.message
    if (error.status === 401) await router.replace('/login')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading">
    <div>
      <h1>借阅记录</h1>
      <p>查看个人申请进度和全馆借阅动态。</p>
    </div>
    <el-button :loading="loading" @click="load">刷新</el-button>
  </section>
  <section class="panel" v-loading="loading">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <el-tabs v-model="tab">
      <el-tab-pane label="我的借阅" name="mine"><LoanTable :rows="mine" /></el-tab-pane>
      <el-tab-pane label="全馆借阅动态" name="all"><LoanTable :rows="all" /></el-tab-pane>
    </el-tabs>
  </section>
</template>
