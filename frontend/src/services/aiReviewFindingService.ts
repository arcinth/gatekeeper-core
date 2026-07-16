import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { AIReviewFinding, AIReviewFindingFilters } from '../types/aiReviewFinding'

export const aiReviewFindingService = {
  async list(filters: AIReviewFindingFilters = {}): Promise<PageResponse<AIReviewFinding>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AIReviewFinding>>>('/ai-review-findings', {
      params: filters,
    })
    return response.data.data
  },
}
