import http, { type ApiResponse } from './http'

export interface PaymentView {
  paymentNo: string
  orderNo: string
  amount: number
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'CLOSED' | 'REFUNDED'
  payUrl: string
  expireAt: string
  paidAt?: string
}

export async function createPayment(orderNo: string): Promise<PaymentView> {
  const { data } = await http.post<ApiResponse<PaymentView>>('/payments', { orderNo })
  return data.data
}

export async function fetchPayment(paymentNo: string): Promise<PaymentView> {
  const { data } = await http.get<ApiResponse<PaymentView>>(`/payments/${paymentNo}`)
  return data.data
}

export async function completeMockPayment(paymentNo: string, success: boolean): Promise<PaymentView> {
  const { data } = await http.post<ApiResponse<PaymentView>>('/payments/mock/complete', { paymentNo, success })
  return data.data
}
