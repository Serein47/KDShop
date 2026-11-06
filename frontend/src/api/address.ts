import http, { type ApiResponse } from './http'

export interface Address {
  id: number
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: number
}

export interface AddressPayload {
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault?: boolean
}

export async function fetchAddresses(): Promise<Address[]> {
  const { data } = await http.get<ApiResponse<Address[]>>('/me/addresses')
  return data.data
}

export async function createAddress(payload: AddressPayload): Promise<Address> {
  const { data } = await http.post<ApiResponse<Address>>('/me/addresses', payload)
  return data.data
}

export async function updateAddress(id: number, payload: AddressPayload): Promise<Address> {
  const { data } = await http.put<ApiResponse<Address>>(`/me/addresses/${id}`, payload)
  return data.data
}

export async function deleteAddress(id: number): Promise<void> {
  await http.delete(`/me/addresses/${id}`)
}

export async function setDefaultAddress(id: number): Promise<void> {
  await http.put(`/me/addresses/${id}/default`)
}
