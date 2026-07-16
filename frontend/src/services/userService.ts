import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { CreateUserRequest, UpdateUserRequest, User } from '../types/user'

export const userService = {
  async list(): Promise<User[]> {
    const response = await apiClient.get<ApiResponse<User[]>>('/users')
    return response.data.data
  },

  async create(request: CreateUserRequest): Promise<User> {
    const response = await apiClient.post<ApiResponse<User>>('/users', request)
    return response.data.data
  },

  async update(id: number, request: UpdateUserRequest): Promise<User> {
    const response = await apiClient.put<ApiResponse<User>>(`/users/${id}`, request)
    return response.data.data
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`/users/${id}`)
  },
}
