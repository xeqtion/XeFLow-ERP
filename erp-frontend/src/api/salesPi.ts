import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './customer'

export interface SalesPiItem {
  id?: number | string
  piId?: number | string
  productId?: number | string
  prodCode?: string
  productName?: string
  productModel?: string
  factoryModel?: string
  unit?: string
  quoteId?: number | string
  quoteSource?: 'SALES_QUOTE' | 'STANDARD_PRICE' | 'DISCOUNT_PRICE' | 'FLOOR_PRICE' | string
  standardPrice?: number
  discountUnitPrice?: number
  floorPrice?: number
  qty?: number
  unitPrice?: number
  amount?: number | null
  remark?: string
}

export interface SalesPi {
  id?: number | string
  piNo: string
  customerId?: number | string
  customerCode?: string
  customerName?: string
  currency?: string
  totalAmount?: number
  depositAmount?: number
  status?: string
  shippedQty?: number
  remainingShipQty?: number
  draftExpireTime?: string
  draftRemainingSeconds?: number
  signDate?: string
  paymentMethod?: string
  tradeTerm?: string
  deliveryDate?: string
  destPort?: string
  shippingMarks?: string
  remark?: string
  createTime?: string
  items?: SalesPiItem[]
}

const BASE = '/sales/pi'

export function pageSalesPi(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  ownerId?: number | string
}): Promise<ApiResult<PageResult<SalesPi>>> {
  return request.get(`${BASE}/page`, { params })
}

export function getSalesPi(id: number | string): Promise<ApiResult<SalesPi>> {
  return request.get(`${BASE}/${id}`)
}

export function createSalesPi(data: SalesPi): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}

export function updateSalesPi(data: SalesPi): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function deleteSalesPi(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function downloadPiDoc(id: number | string, piNo: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/document`, `PI_${piNo}.xlsx`)
}

export function downloadCiDoc(id: number | string, piNo: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/commercial-invoice`, `CI_${piNo}.xlsx`)
}

export function confirmSalesPi(id: number | string): Promise<ApiResult<void>> {
  return request.post(`${BASE}/${id}/confirm`)
}

export function shipSalesPi(id: number | string, params?: { qty?: number; shipDate?: string }): Promise<ApiResult<number | string>> {
  return request.post(`${BASE}/${id}/ship`, null, { params })
}
