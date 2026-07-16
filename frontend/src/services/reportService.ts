import axios from 'axios'
import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { ReportDetail } from '../types/report'

export const reportService = {
  /**
   * Resolves to null on a 404, rather than rejecting - a run without a
   * published report yet (still pending, or never reached a Verdict) is a
   * normal, expected state, not an error the caller should have to catch.
   */
  async getByAnalysisRunId(analysisRunId: number): Promise<ReportDetail | null> {
    try {
      const response = await apiClient.get<ApiResponse<ReportDetail>>(`/analysis-runs/${analysisRunId}/report`)
      return response.data.data
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null
      }
      throw error
    }
  },
}
