import type { AnalysisRunStatus, AnalysisRunTriggerReason, PullRequestStatus } from './analysisRun'
import type { VerdictOutcome } from './verdict'

export interface PullRequestSummary {
  id: number
  number: number
  title: string
  repositoryId: number
  repositoryFullName: string
  repositoryName: string
  repositoryOwner: string
  authorLogin: string
  sourceBranch: string
  targetBranch: string
  status: PullRequestStatus
  githubUrl: string
  latestAnalysisRunId: number | null
  latestAnalysisRunStatus: AnalysisRunStatus | null
  latestVerdictOutcome: VerdictOutcome | null
  createdAt: string
  updatedAt: string
}

export interface PullRequestRepositoryContext {
  id: number
  name: string
  owner: string
  fullName: string
}

export interface PullRequestAnalysisRunReference {
  id: number
  commitSha: string
  status: AnalysisRunStatus
  triggerReason: AnalysisRunTriggerReason
  verdictOutcome: VerdictOutcome | null
  createdAt: string
}

export interface PullRequestDetail {
  id: number
  number: number
  title: string
  repository: PullRequestRepositoryContext
  authorLogin: string
  sourceBranch: string
  targetBranch: string
  headSha: string
  status: PullRequestStatus
  githubUrl: string
  createdAt: string
  updatedAt: string
  analysisRuns: PullRequestAnalysisRunReference[]
}

export interface PullRequestFilters {
  repositoryId?: number
  status?: PullRequestStatus
  page?: number
  size?: number
}
