import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse
} from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

// 请求拦截: 附加 token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('erp_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截: 解包 ApiResult + 统一错误处理 (返回类型交由下方 http 包装断言)
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>): any => {
    // 文件流直接返回
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const res = response.data
    if (res.code === 200) {
      return res
    }
    if (res.code === 401) {
      handleUnauthorized()
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error): any => {
    const status = error.response?.status
    if (status === 401) {
      handleUnauthorized()
    } else if (status === 413) {
      ElMessage.error('文件大小超过上传限制，单个文件不能超过 50MB')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  localStorage.removeItem('erp_token')
  if (location.hash !== '#/login') {
    location.href = '#/login'
    location.reload()
  }
}

/** 下载文件 (带鉴权头), filename 由调用方提供 */
export async function downloadFile(
  url: string,
  filename: string,
  params?: Record<string, unknown>
): Promise<void> {
  let data: Blob
  try {
    data = (await service.get(url, { params, responseType: 'blob' })) as unknown as Blob
  } catch {
    // HTTP 错误: 响应拦截器已弹出提示, 不保存损坏文件
    return
  }
  // 后端业务异常以 HTTP 200 + JSON({code,message}) 返回, blob 模式不会被拦截器识别,
  // 这里检测到 JSON 即视为下载失败, 提示真实错误而不是把错误体存成损坏文件。
  if (data.type && data.type.toLowerCase().includes('application/json')) {
    let msg = '下载失败'
    try {
      const parsed = JSON.parse(await data.text())
      msg = parsed.message || parsed.error || msg
    } catch {
      /* 保底提示 */
    }
    ElMessage.error(msg)
    return
  }
  const blobUrl = window.URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(blobUrl)
}

/** 经响应拦截器后, 实际 resolve 的是 ApiResult<T>, 这里做类型封装 */
const http = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>> {
    return service.get(url, config) as unknown as Promise<ApiResult<T>>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>> {
    return service.post(url, data, config) as unknown as Promise<ApiResult<T>>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>> {
    return service.put(url, data, config) as unknown as Promise<ApiResult<T>>
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>> {
    return service.delete(url, config) as unknown as Promise<ApiResult<T>>
  }
}

export default http
