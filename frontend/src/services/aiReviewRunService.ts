import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { AIReviewRunDetail, AIReviewRunFilters, AIReviewRunSummary } from '../types/aiReviewRun'

export const aiReviewRunService = {
  async list(filters: AIReviewRunFilters = {}): Promise<PageResponse<AIReviewRunSummary>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AIReviewRunSummary>>>('/ai-review-runs', {
      params: filters,
    })
    return response.data.data
  },

  async getById(id: number): Promise<AIReviewRunDetail> {
    const response = await apiClient.get<ApiResponse<AIReviewRunDetail>>(`/ai-review-runs/${id}`)
    return response.data.data
  },
}
