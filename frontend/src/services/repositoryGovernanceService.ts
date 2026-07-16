import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { RepositoryGovernance } from '../types/repositoryGovernance'

export const repositoryGovernanceService = {
  async getByRepositoryId(repositoryId: number, windowDays?: number): Promise<RepositoryGovernance> {
    const response = await apiClient.get<ApiResponse<RepositoryGovernance>>(
      `/repositories/${repositoryId}/governance`,
      { params: windowDays !== undefined ? { windowDays } : undefined },
    )
    return response.data.data
  },
}
