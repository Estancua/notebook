import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: { title: '知识笔记' }
  },
  {
    path: '/note/:noteId',
    name: 'NoteEdit',
    component: () => import('../views/NoteEdit.vue'),
    meta: { title: '编辑笔记' }
  },
  {
    path: '/recycle',
    name: 'RecycleBin',
    component: () => import('../views/RecycleBin.vue'),
    meta: { title: '回收站' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '知识笔记'
  next()
})

export default router
