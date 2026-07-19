import { apiClient } from './apiClient'
import type { ApiResponse, PageResponse } from '../types/api'
import type { AuditLogEntry, AuditLogFilters } from '../types/auditLog'

export const auditLogService = {
  async search(filters: AuditLogFilters = {}): Promise<PageResponse<AuditLogEntry>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AuditLogEntry>>>('/audit-logs', {
      params: filters,
    })
    return response.data.data
  },

  async getById(id: number): Promise<AuditLogEntry> {
    const response = await apiClient.get<ApiResponse<AuditLogEntry>>(`/audit-logs/${id}`)
    return response.data.data
  },

  /** Returns the raw CSV body - callers turn it into a downloadable file client-side. */
  async exportCsv(filters: Omit<AuditLogFilters, 'page' | 'size'> = {}): Promise<Blob> {
    const response = await apiClient.get('/audit-logs/export', {
      params: filters,
      responseType: 'blob',
    })
    return response.data as Blob
  },
}
