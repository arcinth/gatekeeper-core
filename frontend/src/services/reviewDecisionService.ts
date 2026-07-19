import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { CreateReviewDecisionRequest, ReviewDecision } from '../types/reviewDecision'

export const reviewDecisionService = {
  async list(analysisRunId: number): Promise<ReviewDecision[]> {
    const response = await apiClient.get<ApiResponse<ReviewDecision[]>>(
      `/analysis-runs/${analysisRunId}/review-decisions`,
    )
    return response.data.data
  },

  async create(analysisRunId: number, request: CreateReviewDecisionRequest): Promise<ReviewDecision> {
    const response = await apiClient.post<ApiResponse<ReviewDecision>>(
      `/analysis-runs/${analysisRunId}/review-decisions`,
      request,
    )
    return response.data.data
  },
}
