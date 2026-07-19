import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { PullRequestDetail, PullRequestFilters, PullRequestSummary } from '../types/pullRequest'

export const pullRequestService = {
  async list(filters: PullRequestFilters = {}): Promise<PageResponse<PullRequestSummary>> {
    const response = await apiClient.get<ApiResponse<PageResponse<PullRequestSummary>>>('/pull-requests', {
      params: filters,
    })
    return response.data.data
  },

  async getById(id: number): Promise<PullRequestDetail> {
    const response = await apiClient.get<ApiResponse<PullRequestDetail>>(`/pull-requests/${id}`)
    return response.data.data
  },
}
