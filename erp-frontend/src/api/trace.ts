import request, { type ApiResult } from './request'

export interface PiBrief {
  id: number | string
  piNo: string
  customer?: string
  signDate?: string
  ownerId?: number | string
  ownerName?: string
}

export interface TraceOwnerOption {
  id: number | string
  name: string
}

export interface TimelineEvent {
  node: string
  nodeLabel: string
  docNo?: string
  date?: string
  status?: string
  summary?: string
}

export interface PiTraceItem {
  productId?: number | string
  prodCode?: string
  productName?: string
  productModel?: string
  factoryModel?: string
  unit?: string
  quoteId?: number | string
  qty?: number
  unitPrice?: number
  amount?: number
  remark?: string
}

export interface PiTrace {
  piId: number | string
  piNo: string
  ownerId?: number | string
  ownerName?: string
  customerCode?: string
  customer?: string
  currency?: string
  totalAmount?: number
  status?: string
  signDate?: string
  deliveryDate?: string
  paymentMethod?: string
  tradeTerm?: string
  items?: PiTraceItem[]
  events: TimelineEvent[]
}

export function piList(params?: { ownerId?: number | string | null }): Promise<ApiResult<PiBrief[]>> {
  return request.get('/trace/pi-list', { params })
}

export function tracePi(id: number | string): Promise<ApiResult<PiTrace>> {
  return request.get(`/trace/pi/${id}`)
}

export function traceOwners(): Promise<ApiResult<TraceOwnerOption[]>> {
  return request.get('/trace/owners')
}
