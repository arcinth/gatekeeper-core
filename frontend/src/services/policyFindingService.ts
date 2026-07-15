import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { PolicyFinding, PolicyFindingFilters } from '../types/policyFinding'

export const policyFindingService = {
  async list(filters: PolicyFindingFilters = {}): Promise<PageResponse<PolicyFinding>> {
    const response = await apiClient.get<ApiResponse<PageResponse<PolicyFinding>>>('/policy-findings', {
      params: filters,
    })
    return response.data.data
  },
}
