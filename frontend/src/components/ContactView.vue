<script setup>
import { reactive, ref } from 'vue'
import { account, api } from '../api'

const contactEmail = import.meta.env.VITE_CONTACT_EMAIL?.trim() || '2411557902@qq.com'
const form = reactive({ subject: '', message: '' })
const sending = ref(false)
const submitted = ref(false)
const errorMessage = ref('')

async function submit() {
  if (sending.value) return
  errorMessage.value = ''
  submitted.value = false
  const subject = form.subject.trim()
  const message = form.message.trim()
  if (!subject || !message) {
    errorMessage.value = '请填写反馈主题和内容'
    return
  }
  sending.value = true
  try {
    await api('/feedback', { method: 'POST', body: { subject, message } })
    Object.assign(form, { subject: '', message: '' })
    submitted.value = true
  } catch (error) {
    errorMessage.value = error.status === 401 ? '登录已过期，请重新登录后提交。' : error.message
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="page-heading">
    <div>
      <h1>联系我们</h1>
      <p>提交使用建议、报告问题，或获取借阅和归还帮助。</p>
    </div>
  </section>
  <div class="contact-layout">
    <section class="panel prose-panel feedback-panel" aria-labelledby="feedback">
      <h2 id="feedback">意见反馈</h2>
      <p>提交的反馈会送达管理员。咨询借阅时，可附上书名和借阅编号。</p>
      <el-alert v-if="submitted" title="反馈已提交，管理员可在后台查看。" type="success" :closable="false" class="form-error" />
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" class="form-error" />
      <el-form v-if="account?.role === 'READER'" label-position="top" :disabled="sending" @submit.prevent="submit">
        <el-form-item label="反馈主题" required>
          <el-input v-model="form.subject" maxlength="100" show-word-limit placeholder="简要描述你的建议或问题" />
        </el-form-item>
        <el-form-item label="反馈内容" required>
          <el-input v-model="form.message" type="textarea" :rows="6" maxlength="2000" show-word-limit
            placeholder="例如出现问题的页面、操作步骤和结果" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="sending">提交反馈</el-button>
      </el-form>
      <RouterLink v-else-if="!account" class="primary-link" :to="{ name: 'login', query: { redirect: '/contact' } }">
        登录后提交反馈
      </RouterLink>
      <RouterLink v-else class="primary-link" to="/admin">打开管理后台</RouterLink>
    </section>
    <aside class="contact-help" aria-label="联系渠道和常见问题">
      <section class="panel prose-panel" aria-labelledby="contact">
        <h2 id="contact">联系邮箱</h2>
        <a class="contact-email" :href="'mailto:' + contactEmail">{{ contactEmail }}</a>
      </section>
      <section class="panel prose-panel" aria-labelledby="faq">
        <h2 id="faq">常见问题</h2>
        <details>
          <summary>为什么无法申请借阅？</summary>
          <p>请确认已登录读者账号、该书有可借库存，并且名下没有同一本书的待审批或已借出记录。</p>
        </details>
        <details>
          <summary>在哪里确认归还？</summary>
          <p>交还实体图书后，可从书籍管理或个人中心登记归还。</p>
        </details>
        <details>
          <summary>如何查看借阅编号？</summary>
          <p>登录后进入个人中心，在“我的借阅”查看每条记录的编号。</p>
        </details>
      </section>
    </aside>
  </div>
</template>
