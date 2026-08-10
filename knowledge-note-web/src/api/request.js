import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    const msg = res.msg || '请求失败'
    if (window.__toastFn) {
      window.__toastFn(msg, 'error')
    }
    return Promise.reject(new Error(msg))
  },
  (error) => {
    let msg = '网络连接失败，请检查网络'
    if (error.code === 'ECONNABORTED' && error.message.includes('timeout')) {
      msg = '请求超时，请重试'
    }
    if (window.__toastFn) {
      window.__toastFn(msg, 'error')
    }
    return Promise.reject(error)
  }
)

export default request
