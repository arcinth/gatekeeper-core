import type { AIReviewConfidence, AIReviewFindingType } from './aiReviewFinding'
import type { AIReviewRunStatus } from './aiReviewRun'
import type { AnalysisRunStatus } from './analysisRun'
import type { PolicyCategory, PolicySeverity } from './policyFinding'
import type { AiReviewStatus } from './report'
import type { SecurityCategory, SecuritySeverity } from './securityFinding'
import type { VerdictOutcome } from './verdict'

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
  totalSecurityFindings: number
  securityFindingsBySeverity: Partial<Record<SecuritySeverity, number>>
  securityFindingsByCategory: Partial<Record<SecurityCategory, number>>
  totalAiReviewRuns: number
  aiReviewRunsByStatus: Partial<Record<AIReviewRunStatus, number>>
  totalAiReviewFindings: number
  aiReviewFindingsByConfidence: Partial<Record<AIReviewConfidence, number>>
  aiReviewFindingsByType: Partial<Record<AIReviewFindingType, number>>
  totalVerdicts: number
  verdictsByOutcome: Partial<Record<VerdictOutcome, number>>
  totalReportsPublished: number
  reportsByAiStatus: Partial<Record<AiReviewStatus, number>>
}
