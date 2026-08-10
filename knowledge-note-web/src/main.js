import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(router)

// Toast 全局工具函数
window.__toastFn = null
app.config.globalProperties.$toast = (msg, type = 'info') => {
  if (window.__toastFn) {
    window.__toastFn(msg, type)
  }
}

app.mount('#app')
