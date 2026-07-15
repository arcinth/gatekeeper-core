import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { DashboardOverview, DashboardStatus } from '../types/dashboard'

export const dashboardService = {
  async getStatus(): Promise<DashboardStatus> {
    const response = await apiClient.get<ApiResponse<DashboardStatus>>('/dashboard')
    return response.data.data
  },

  async getOverview(windowDays?: number): Promise<DashboardOverview> {
    const response = await apiClient.get<ApiResponse<DashboardOverview>>('/dashboard/overview', {
      params: windowDays !== undefined ? { windowDays } : undefined,
    })
    return response.data.data
  },
}
