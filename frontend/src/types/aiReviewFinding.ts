export type AIReviewConfidence = 'LOW' | 'MEDIUM' | 'HIGH'
export type AIReviewFindingType = 'SUGGESTION' | 'POTENTIAL_BUG' | 'STYLE' | 'PERFORMANCE' | 'CLARITY'

export interface AIReviewFinding {
  id: number
  aiReviewRunId: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  type: AIReviewFindingType
  confidence: AIReviewConfidence
  filePath: string
  lineNumber: number | null
  message: string
  recommendation: string | null
  createdAt: string
}

export interface AIReviewFindingFilters {
  aiReviewRunId?: number
  analysisRunId?: number
  repositoryId?: number
  confidence?: AIReviewConfidence
  type?: AIReviewFindingType
  page?: number
  size?: number
  sort?: string
}
