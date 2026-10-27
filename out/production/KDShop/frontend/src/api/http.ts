import axios from 'axios'

/** 与后端约定的统一响应体，见 docs/接口文档/README.md */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true,
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    // 401 由页面跳登录，403 交给调用方提示；这里只兜网络与 5xx
    return Promise.reject(error)
  },
)

export default http
