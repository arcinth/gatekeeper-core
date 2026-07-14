import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { DashboardStatus } from '../types/dashboard'

export const dashboardService = {
  async getStatus(): Promise<DashboardStatus> {
    const response = await apiClient.get<ApiResponse<DashboardStatus>>('/dashboard')
    return response.data.data
  },
}
