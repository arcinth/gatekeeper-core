import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { SecurityFinding, SecurityFindingFilters } from '../types/securityFinding'

export const securityFindingService = {
  async list(filters: SecurityFindingFilters = {}): Promise<PageResponse<SecurityFinding>> {
    const response = await apiClient.get<ApiResponse<PageResponse<SecurityFinding>>>('/security-findings', {
      params: filters,
    })
    return response.data.data
  },
}
