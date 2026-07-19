import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { PolicyConfiguration, UpdatePolicyConfigurationRequest } from '../types/policyConfiguration'

export const policyConfigurationService = {
  async list(): Promise<PolicyConfiguration[]> {
    const response = await apiClient.get<ApiResponse<PolicyConfiguration[]>>('/policies')
    return response.data.data
  },

  async update(ruleId: string, request: UpdatePolicyConfigurationRequest): Promise<PolicyConfiguration> {
    const response = await apiClient.put<ApiResponse<PolicyConfiguration>>(`/policies/${ruleId}`, request)
    return response.data.data
  },

  async resetToDefault(ruleId: string): Promise<PolicyConfiguration> {
    const response = await apiClient.delete<ApiResponse<PolicyConfiguration>>(`/policies/${ruleId}`)
    return response.data.data
  },
}
