import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { account, restoreAccount } from './api'
import CatalogView from './views/CatalogView.vue'
import AuthView from './views/AuthView.vue'
import LoansView from './views/LoansView.vue'
import AdminView from './views/AdminView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'catalog', component: CatalogView },
    { path: '/login', name: 'login', component: AuthView },
    { path: '/register', name: 'register', component: AuthView },
    { path: '/loans', name: 'loans', component: LoansView, meta: { reader: true } },
    { path: '/admin', name: 'admin', component: AdminView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

let restored = false
router.beforeEach(async (to) => {
  if (!restored) {
    try {
      await restoreAccount()
      restored = true
    } catch (error) {
      ElMessage.error(error.message)
    }
  }
  if (to.meta.reader && account.value?.role !== 'READER') return { name: 'login' }
  if (['login', 'register'].includes(to.name) && account.value?.role === 'READER') return '/'
})

export default router
