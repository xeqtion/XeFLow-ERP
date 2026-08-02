import request, { type ApiResult } from './request'
import type { PageResult } from './system'

export interface CurrencyRate {
  id?: number
  currency: string
  currencyName?: string
  rateToBase: number | null
  effectiveDate?: string
  remark?: string
  createTime?: string
}

export interface CurrencyOption {
  code: string
  name: string
  base: boolean
}

export interface CurrencyConvertResult {
  base: string
  date: string
  from: string
  to: string
  rate: number
  fromRateToBase: number
  toRateToBase: number
  convertedAmount: number
  baseAmount: number
}

const BASE = '/basedata/currency'

export function pageCurrency(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  date?: string
}): Promise<ApiResult<PageResult<CurrencyRate>>> {
  return request.get(`${BASE}/page`, { params })
}

export function listCurrency(): Promise<ApiResult<CurrencyRate[]>> {
  return request.get(`${BASE}/list`)
}

export function baseCurrency(): Promise<ApiResult<{ base: string }>> {
  return request.get(`${BASE}/base`)
}

export function listSupportedCurrencies(): Promise<ApiResult<CurrencyOption[]>> {
  return request.get(`${BASE}/currencies`)
}

export function createCurrency(data: CurrencyRate): Promise<ApiResult<number>> {
  return request.post(BASE, data)
}

export function updateCurrency(data: CurrencyRate): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function deleteCurrency(id: number): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function convertCurrency(params: {
  amount: number
  from: string
  to: string
  date?: string
}): Promise<ApiResult<CurrencyConvertResult>> {
  return request.get(`${BASE}/convert`, { params })
}

export function refreshOnlineRates(): Promise<
  ApiResult<{ base: string; date: string; rates: Record<string, number>; failed: Record<string, string> }>
> {
  return request.post(`${BASE}/refresh-online`)
}
