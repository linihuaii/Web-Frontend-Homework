import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { account, restoreAccount } from './api'
import HomeView from './components/HomeView.vue'
import CatalogView from './components/CatalogView.vue'
import AuthView from './components/AuthView.vue'
import ProfileView from './components/ProfileView.vue'
import AdminView from './components/AdminView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/books', name: 'catalog', component: CatalogView },
    { path: '/about', name: 'about', component: () => import('./components/AboutView.vue') },
    { path: '/contact', name: 'contact', component: () => import('./components/ContactView.vue') },
    { path: '/login', name: 'login', component: AuthView },
    { path: '/register', name: 'register', component: AuthView },
    { path: '/profile', alias: '/loans', name: 'profile', component: ProfileView, meta: { reader: true } },
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
  if (to.meta.reader && account.value?.role !== 'READER') {
    if (account.value?.role === 'ADMIN') return { name: 'admin' }
    return { name: 'login', query: { redirect: '/profile' } }
  }
  if (['login', 'register'].includes(to.name) && account.value?.role === 'READER') return '/'
})

export default router
