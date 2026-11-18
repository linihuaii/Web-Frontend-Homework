<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { account, api } from './api'

const route = useRoute()
const router = useRouter()

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST', body: {} })
    account.value = null
    if (route.name !== 'admin') await router.push('/')
  } catch (error) {
    ElMessage.error(error.message)
  }
}
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <RouterLink class="brand" to="/">
        <span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span>
        图书管理系统
      </RouterLink>
      <nav aria-label="主导航">
        <RouterLink to="/">馆藏目录</RouterLink>
        <RouterLink v-if="account?.role === 'READER'" to="/loans">借阅记录</RouterLink>
        <RouterLink to="/admin">管理后台</RouterLink>
      </nav>
      <div class="session">
        <template v-if="account">
          <span>{{ account.username }}</span>
          <el-button text @click="logout">退出</el-button>
        </template>
        <RouterLink v-else class="login-link" to="/login">读者登录</RouterLink>
      </div>
    </div>
  </header>
  <main class="shell">
    <RouterView />
  </main>
</template>
