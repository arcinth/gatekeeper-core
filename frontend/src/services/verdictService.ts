import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { VerdictDetail, VerdictFilters, VerdictSummary } from '../types/verdict'

export const verdictService = {
  async list(filters: VerdictFilters = {}): Promise<PageResponse<VerdictSummary>> {
    const response = await apiClient.get<ApiResponse<PageResponse<VerdictSummary>>>('/verdicts', {
      params: filters,
    })
    return response.data.data
  },

  async getById(id: number): Promise<VerdictDetail> {
    const response = await apiClient.get<ApiResponse<VerdictDetail>>(`/verdicts/${id}`)
    return response.data.data
  },
}
