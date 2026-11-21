<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { account, api } from '../api'

const props = defineProps({ admin: Boolean })
const route = useRoute()
const router = useRouter()
const registering = computed(() => !props.admin && route.name === 'register')
const title = computed(() => props.admin ? '管理员登录' : registering.value ? '注册读者账号' : '读者登录')
const form = reactive({ username: '', password: '' })
const busy = ref(false)
const errorMessage = ref('')
watch(() => route.name, () => {
  errorMessage.value = ''
  form.password = ''
})

async function submit() {
  if (busy.value) return
  errorMessage.value = ''
  const username = form.username.trim()
  if (!/^[A-Za-z0-9_]{3,32}$/.test(username)) {
    errorMessage.value = '用户名须为 3–32 位字母、数字或下划线'
    return
  }
  if (form.password.length < 8 || new TextEncoder().encode(form.password).length > 72) {
    errorMessage.value = '密码至少 8 个字符，且最多 72 字节'
    return
  }
  const isRegistration = registering.value
  const endpoint = props.admin ? '/auth/admin-login' : isRegistration ? '/auth/register' : '/auth/login'
  busy.value = true
  try {
    const result = await api(endpoint, { method: 'POST', body: { username, password: form.password } })
    if (isRegistration) {
      ElMessage.success('注册成功，请登录')
      await router.push('/login')
    } else {
      account.value = result
      if (!props.admin) {
        const destination = route.query.redirect
        await router.push(['/books', '/profile', '/contact'].includes(destination) ? destination : '/books')
      }
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="auth-card">
    <h1>{{ title }}</h1>
    <form @submit.prevent="submit">
      <el-form-item label="用户名" label-position="top">
        <el-input v-model="form.username" aria-label="用户名" autocomplete="username" maxlength="32" />
      </el-form-item>
      <el-form-item label="密码" label-position="top">
        <el-input v-model="form.password" aria-label="密码" type="password" show-password
          :autocomplete="registering ? 'new-password' : 'current-password'" />
      </el-form-item>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" class="form-error" />
      <el-button native-type="submit" type="primary" :loading="busy" class="full-width">
        {{ registering ? '注册' : '登录' }}
      </el-button>
    </form>
    <p v-if="!admin" class="auth-switch">
      <RouterLink :to="registering ? '/login' : '/register'">{{ registering ? '已有账号，去登录' : '注册读者账号' }}</RouterLink>
    </p>
  </section>
</template>
