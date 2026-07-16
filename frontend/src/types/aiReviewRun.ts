import type { AIReviewConfidence } from './aiReviewFinding'

export type AIReviewRunStatus = 'COMPLETED' | 'FAILED'

export interface AIReviewRunSummary {
  id: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  status: AIReviewRunStatus
  provider: string
  model: string
  promptVersion: string
  createdAt: string
  updatedAt: string
  findingsTotal: number
}

export interface AIReviewRunDetail {
  id: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  status: AIReviewRunStatus
  provider: string
  model: string
  promptVersion: string
  summary: string | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
  findingsByConfidence: Partial<Record<AIReviewConfidence, number>>
}

export interface AIReviewRunFilters {
  analysisRunId?: number
  repositoryId?: number
  status?: AIReviewRunStatus
  provider?: string
  page?: number
  size?: number
  sort?: string
}
