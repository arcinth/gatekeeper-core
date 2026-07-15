import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { Repository } from '../types/repository'

export const repositoryService = {
  async list(): Promise<Repository[]> {
    const response = await apiClient.get<ApiResponse<Repository[]>>('/repositories')
    return response.data.data
  },
}
