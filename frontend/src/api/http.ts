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

let csrfToken: string | null = null

/** 取 CSRF token：登录前也要先拿，否则 POST 会被 403 */
export async function ensureCsrf(force = false): Promise<string> {
  if (csrfToken && !force) return csrfToken
  const { data } = await axios.get<ApiResponse<{ token: string }>>('/api/v1/auth/csrf', {
    withCredentials: true,
  })
  csrfToken = data.data.token
  return csrfToken
}

http.interceptors.request.use(async (config) => {
  const method = (config.method ?? 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method)) {
    config.headers = config.headers ?? {}
    config.headers['X-XSRF-TOKEN'] = await ensureCsrf()
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    // CSRF token 过期：换一次再重试
    if (error?.response?.status === 403 && !error.config?.__retried) {
      error.config.__retried = true
      await ensureCsrf(true)
      return http.request(error.config)
    }
    return Promise.reject(error)
  },
)

export default http
