import type { AIReviewConfidence, AIReviewFindingType } from './aiReviewFinding'
import type { AnalysisRunStatus } from './analysisRun'
import type { PolicyCategory, PolicySeverity } from './policyFinding'
import type { AiReviewStatus } from './report'
import type { SecurityCategory, SecuritySeverity } from './securityFinding'
import type { VerdictOutcome } from './verdict'

// Field-for-field mirror of the backend's RepositoryGovernanceResponse
// (Repository Governance View Architecture, Section 7) - the Dashboard's own
// overview shape, scoped to one repository. Deliberately its own type, not a
// reuse of DashboardOverview: this has no totalRepositories, and has no
// totalAiReviewRuns/aiReviewRunsByStatus either - Milestone 1's backend only
// aggregates AI review *findings* per repository, not AI review *run* counts.
export interface RepositoryGovernance {
  repositoryId: number
  repositoryFullName: string
  windowDays: number
  totalAnalysisRuns: number
  runsByStatus: Partial<Record<AnalysisRunStatus, number>>
  totalFindings: number
  findingsBySeverity: Partial<Record<PolicySeverity, number>>
  findingsByCategory: Partial<Record<PolicyCategory, number>>
  totalSecurityFindings: number
  securityFindingsBySeverity: Partial<Record<SecuritySeverity, number>>
  securityFindingsByCategory: Partial<Record<SecurityCategory, number>>
  totalAiReviewFindings: number
  aiReviewFindingsByConfidence: Partial<Record<AIReviewConfidence, number>>
  aiReviewFindingsByType: Partial<Record<AIReviewFindingType, number>>
  totalVerdicts: number
  verdictsByOutcome: Partial<Record<VerdictOutcome, number>>
  totalReportsPublished: number
  reportsByAiStatus: Partial<Record<AiReviewStatus, number>>
}
