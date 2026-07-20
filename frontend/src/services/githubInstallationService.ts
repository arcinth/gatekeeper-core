import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { GitHubInstallation, InstallUrl } from '../types/githubInstallation'

export const githubInstallationService = {
  async getInstallUrl(): Promise<InstallUrl> {
    const response = await apiClient.get<ApiResponse<InstallUrl>>('/github/install-url')
    return response.data.data
  },

  async list(): Promise<GitHubInstallation[]> {
    const response = await apiClient.get<ApiResponse<GitHubInstallation[]>>('/github/installations')
    return response.data.data
  },

  async sync(id: number): Promise<GitHubInstallation> {
    const response = await apiClient.post<ApiResponse<GitHubInstallation>>(`/github/installations/${id}/sync`)
    return response.data.data
  },
}
