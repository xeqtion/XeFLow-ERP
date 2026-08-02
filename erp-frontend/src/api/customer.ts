import request, { type ApiResult, downloadFile } from './request'

export interface Customer {
  id?: number | string
  ownerId?: number | string | null
  ownerName?: string
  poolType?: 'PERSONAL' | 'PUBLIC'
  ownerAssignedAt?: string | null
  custCode: string
  contactName?: string
  contactPosition?: string
  country?: string
  companyName?: string
  address?: string
  currency?: string
  website?: string
  phone?: string
  email?: string
  socialContact?: string
  remark?: string
  custNameCn?: string
  custNameEn?: string
  businessLicense?: string
  taxNo?: string
  bankName?: string
  bankAccount?: string
  contact?: string
  creditLimit?: number | null
  createTime?: string
}

export interface CustomerExtraField {
  id?: number | string
  customerId?: number | string
  fieldKey: string
  fieldValue?: string
  sort?: number
}

export interface CustomerAssigneeOption {
  id: number | string
  name: string
  username: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface ImportResult {
  total: number
  success: number
  fail: number
  warning: number
  errors: { row: number; message: string }[]
  details: {
    row: number
    message?: string
    status: 'SUCCESS' | 'WARNING' | 'ERROR'
    factoryModel?: string
    productName?: string
  }[]
}

const BASE = '/basedata/customer'

export function pageCustomer(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  poolType?: 'PERSONAL' | 'PUBLIC' | 'ALL'
}): Promise<ApiResult<PageResult<Customer>>> {
  return request.get(`${BASE}/page`, { params })
}

export function getCustomer(id: number | string): Promise<ApiResult<Customer>> {
  return request.get(`${BASE}/${id}`)
}

export function createCustomer(data: Customer): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}

export function updateCustomer(data: Customer): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function listCustomerAssignees(): Promise<ApiResult<CustomerAssigneeOption[]>> {
  return request.get(`${BASE}/assignees`)
}

export function assignCustomerAssignee(
  customerId: number | string,
  ownerId: number | string
): Promise<ApiResult<void>> {
  return request.put(`${BASE}/${customerId}/assignee`, { ownerId })
}

export function transferCustomerToPublicPool(
  customerId: number | string,
  reason?: string
): Promise<ApiResult<void>> {
  return request.put(`${BASE}/${customerId}/public-pool`, { reason: reason?.trim() || undefined })
}

export function deleteCustomer(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function listCustomerExtras(customerId: number | string): Promise<ApiResult<CustomerExtraField[]>> {
  return request.get(`${BASE}/${customerId}/extras`)
}

export function saveCustomerExtras(
  customerId: number | string,
  fields: CustomerExtraField[]
): Promise<ApiResult<void>> {
  return request.put(`${BASE}/${customerId}/extras`, fields)
}

export function importCustomer(file: File): Promise<ApiResult<ImportResult>> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${BASE}/import`, form)
}

export function exportCustomer(keyword?: string, poolType?: 'PERSONAL' | 'PUBLIC' | 'ALL'): Promise<void> {
  return downloadFile(`${BASE}/export`, '客户资料.xlsx', { keyword, poolType })
}

export function downloadCustomerTemplate(): Promise<void> {
  return downloadFile(`${BASE}/template`, '客户资料导入模板.xlsx')
}

export function downloadCustomerDoc(id: number | string, code: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/document`, `客户资料表_${code}.docx`)
}

/* ---------------- 客户附件 ---------------- */
export interface CustomerFile {
  id: number | string
  customerId: number | string
  fileName: string
  contentType?: string
  size?: number
  uploaderName?: string
  remark?: string
  createTime?: string
}

export function listCustomerFiles(customerId: number | string): Promise<ApiResult<CustomerFile[]>> {
  return request.get(`${BASE}/${customerId}/files`)
}

export function uploadCustomerFile(customerId: number | string, file: File, remark?: string): Promise<ApiResult<CustomerFile>> {
  const form = new FormData()
  form.append('file', file)
  if (remark) form.append('remark', remark)
  return request.post(`${BASE}/${customerId}/files`, form)
}

export function downloadCustomerFile(fileId: number | string, fileName: string): Promise<void> {
  return downloadFile(`${BASE}/files/${fileId}/download`, fileName)
}

export function deleteCustomerFile(fileId: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/files/${fileId}`)
}
