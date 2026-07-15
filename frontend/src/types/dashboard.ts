import type { AnalysisRunStatus } from './analysisRun'
import type { PolicyCategory, PolicySeverity } from './policyFinding'

export interface DashboardStatus {
  status: string
  version: string
}

export interface DashboardOverview {
  windowDays: number
  totalRepositories: number
  totalAnalysisRuns: number
  runsByStatus: Partial<Record<AnalysisRunStatus, number>>
  totalFindings: number
  findingsBySeverity: Partial<Record<PolicySeverity, number>>
  findingsByCategory: Partial<Record<PolicyCategory, number>>
}
