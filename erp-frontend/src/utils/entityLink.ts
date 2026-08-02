import { type RouteLocationRaw } from 'vue-router'
import router from '@/router'

/* ======================== 类型定义 ======================== */

/** 实体类型注册配置 */
export interface EntityTypeConfig {
  /** 实体类型标识, 如 'product', 'purchase_order' */
  type: string
  /** 显示名称, 如 '产品', 'PO采购订单' */
  label: string
  /** 获取路由地址 */
  getRoute(id: string | number, extra?: Record<string, unknown>): RouteLocationRaw
  /** 获取显示名称（用于卡片标题） */
  getDisplayName(id: string | number, data?: Record<string, unknown>): string
  /** 所需权限（可选, 为空则不检查） */
  permission?: string
  /** 实体展示的详情字段（可选, 用于详情摘要） */
  detailFields?: { key: string; label: string }[]
  /** 加载实体列表（可选, 用于"分享数据"弹窗的选择步骤）。解耦关键: 每个实体类型自己定义如何加载数据 */
  loader?: (keyword: string) => Promise<any[]>
}

/** 实体引用（消息内容中存储的结构化数据） */
export interface EntityRef {
  __entity: true
  type: string
  id: number | string
  name: string
  extra?: Record<string, unknown>
}

/** 解析后的实体引用 + 类型配置 */
export interface EntityRefResolved extends EntityRef {
  config: EntityTypeConfig
}

/* ======================== 注册表 ======================== */

const registry = new Map<string, EntityTypeConfig>()

/**
 * 注册一个实体类型。
 * 解耦核心: 各业务模块独立注册, 消息中心无需关心具体实体类型。
 */
export function registerEntityType(config: EntityTypeConfig) {
  registry.set(config.type, config)
}

export function getEntityTypeConfig(type: string): EntityTypeConfig | undefined {
  return registry.get(type)
}

export function getRegisteredTypes(): EntityTypeConfig[] {
  return Array.from(registry.values())
}

/* ======================== 编码 / 解码 ======================== */

/** 将实体引用编码为消息内容中可嵌入的文本 */
export function encodeEntityRef(ref: Omit<EntityRef, '__entity'>): string {
  const payload: EntityRef = { __entity: true, ...ref }
  return JSON.stringify(payload)
}

/** 判断文本中是否包含实体引用标记 */
export function containsEntityRef(text: string | null | undefined): boolean {
  if (!text) return false
  return text.includes('"__entity":true')
}

/** 从文本末尾提取 JSON 块 */
function extractJSONBlock(text: string): string | null {
  // 先尝试整体 parse
  try {
    JSON.parse(text)
    return text
  } catch {
    /* 不是纯 JSON */
  }
  // 尝试从末尾提取 JSON 块: 查找最后一个 '{'
  const lastBrace = text.lastIndexOf('{')
  if (lastBrace === -1) return null
  const candidate = text.slice(lastBrace)
  try {
    JSON.parse(candidate)
    return candidate
  } catch {
    return null
  }
}

/** 从一段文本中提取实体引用。支持 纯JSON 或 "附言\n{JSON}" 格式 */
export function tryParseEntityRef(text: string | null | undefined): EntityRef | null {
  if (!text) return null
  const block = extractJSONBlock(text)
  if (!block) return null
  try {
    const obj = JSON.parse(block)
    if (
      obj &&
      obj.__entity === true &&
      typeof obj.type === 'string' &&
      obj.type.length > 0 &&
      obj.id != null
    ) {
      return obj as EntityRef
    }
  } catch {
    /* not JSON */
  }
  return null
}

/* ======================== 解析与权限 ======================== */

/** 解析实体引用, 附加类型配置信息。如果类型未注册返回 null */
export function resolveEntityRef(ref: EntityRef): EntityRefResolved | null {
  const config = getEntityTypeConfig(ref.type)
  if (!config) return null
  return { ...ref, config }
}

/**
 * 检查当前用户是否有权限查看该实体。
 * 需要传 userStore.hasPermission 引用以保持响应式解耦。
 */
export function checkEntityPermission(
  resolved: EntityRefResolved,
  hasPermission: (perm: string) => boolean
): boolean {
  if (!resolved.config.permission) return true
  return hasPermission(resolved.config.permission)
}

/* ======================== 导航 ======================== */

/** 点击实体卡片时跳转到对应页面 */
export function navigateToEntity(ref: EntityRefResolved) {
  const route = ref.config.getRoute(ref.id, ref.extra)
  router.push(route)
}

/* ======================== 内置实体类型注册 ======================== */

/** 初始化所有内置实体类型注册 */
export function initDefaultEntityTypes() {
  // 产品管理
  registerEntityType({
    type: 'product',
    label: '产品',
    permission: 'product:update',
    detailFields: [
      { key: 'companyModel', label: '公司型号' },
      { key: 'factoryModel', label: '工厂型号' },
      { key: 'factoryPrice', label: '出厂价' },
      { key: 'currency', label: '币种' }
    ],
    getRoute() {
      return { path: '/basedata/product' }
    },
    getDisplayName(_id, data) {
      return (data?.productName as string) || `产品[${_id}]`
    },
    async loader(keyword) {
      const { pageProduct } = await import('@/api/product')
      const res = await pageProduct({ pageNum: 1, pageSize: 50, keyword })
      return res.data.records
    }
  })

  // PO采购订单
  registerEntityType({
    type: 'purchase_order',
    label: 'PO采购订单',
    permission: 'purchase_order:update',
    detailFields: [
      { key: 'poNo', label: 'PO单号' },
      { key: 'supplier', label: '供应商' },
      { key: 'totalAmount', label: '金额' },
      { key: 'currency', label: '币种' },
      { key: 'status', label: '状态' }
    ],
    getRoute() {
      return { path: '/order/po' }
    },
    getDisplayName(_id, data) {
      return (data?.poNo as string) || `PO订单[${_id}]`
    },
    async loader(keyword) {
      const { pagePo } = await import('@/api/purchaseOrder')
      const res = await pagePo({ pageNum: 1, pageSize: 50, keyword })
      return res.data.records
    }
  })

  // ... 后续可在此扩展更多实体类型
}
