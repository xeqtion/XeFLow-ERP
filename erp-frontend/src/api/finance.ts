import request, { type ApiResult, downloadFile } from './request'
import type { PageResult, ImportResult } from './customer'

export interface FinanceEntry {
  id?: number
  entryNo?: string
  type: string
  direction?: string
  customerId?: number | string
  customerCode?: string
  amount?: number
  currency?: string
  relatedDocType?: string
  relatedDocNo?: string
  entryDate?: string
  remark?: string
  createTime?: string
}

export interface FinanceType {
  code: string
  label: string
  direction: string
}

export interface FinanceSummary {
  type: string
  label: string
  direction: string
  totalAmount: number
  count: number
  currency: string
}

export interface FinanceQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  type?: string
  customerId?: number | string
  customerCode?: string
  startDate?: string
  endDate?: string
}

const BASE = '/finance/entry'

export function financeTypes(): Promise<ApiResult<FinanceType[]>> {
  return request.get(`${BASE}/types`)
}
export function pageFinance(params: FinanceQuery): Promise<ApiResult<PageResult<FinanceEntry>>> {
  return request.get(`${BASE}/page`, { params })
}
export function financeSummary(params: Partial<FinanceQuery>): Promise<ApiResult<FinanceSummary[]>> {
  return request.get(`${BASE}/summary`, { params })
}
export function createFinance(data: FinanceEntry): Promise<ApiResult<number>> {
  return request.post(BASE, data)
}
export function updateFinance(data: FinanceEntry): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}
export function deleteFinance(id: number): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}
export function importFinance(file: File): Promise<ApiResult<ImportResult>> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${BASE}/import`, form)
}
export function exportFinance(params: Partial<FinanceQuery>): Promise<void> {
  return downloadFile(`${BASE}/export`, '收付款流水.xlsx', params as Record<string, unknown>)
}
export function downloadFinanceTemplate(): Promise<void> {
  return downloadFile(`${BASE}/template`, '收付款导入模板.xlsx')
}
