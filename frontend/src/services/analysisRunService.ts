import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { AnalysisRunDetail, AnalysisRunFilters, AnalysisRunSummary } from '../types/analysisRun'

export const analysisRunService = {
  async list(filters: AnalysisRunFilters = {}): Promise<PageResponse<AnalysisRunSummary>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AnalysisRunSummary>>>('/analysis-runs', {
      params: filters,
    })
    return response.data.data
  },

  async getById(id: number): Promise<AnalysisRunDetail> {
    const response = await apiClient.get<ApiResponse<AnalysisRunDetail>>(`/analysis-runs/${id}`)
    return response.data.data
  },
}
