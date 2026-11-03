import type { OrderStatus } from '@/api/order'

const ORDER_LABELS: Record<string, string> = {
  PENDING_PAYMENT: '待付款',
  PAID: '已付款',
  PARTIALLY_SHIPPED: '部分发货',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
}

const SUB_ORDER_LABELS: Record<string, string> = {
  ...ORDER_LABELS,
  PENDING_SHIPMENT: '待发货',
  REFUNDING: '退款中',
}

export function orderStatusLabel(status?: string | null): string {
  if (!status) return '未知'
  return ORDER_LABELS[status] ?? status
}

export function subOrderStatusLabel(status?: string | null): string {
  if (!status) return '未知'
  return SUB_ORDER_LABELS[status] ?? status
}

export function isPayable(status: OrderStatus | string): boolean {
  return status === 'PENDING_PAYMENT'
}

export function isCancellable(status: OrderStatus | string): boolean {
  return status === 'PENDING_PAYMENT'
}

export function orderStatusTone(status?: string | null): 'wait' | 'moving' | 'done' | 'muted' {
  if (status === 'PENDING_PAYMENT') return 'wait'
  if (status === 'COMPLETED') return 'done'
  if (status === 'CANCELLED' || status === 'REFUNDED') return 'muted'
  return 'moving'
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
