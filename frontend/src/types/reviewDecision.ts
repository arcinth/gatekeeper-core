export type ReviewDecisionType = 'APPROVED' | 'REJECTED'

export interface ReviewDecision {
  id: number
  analysisRunId: number
  decision: ReviewDecisionType
  comment: string | null
  reviewerId: number
  reviewerName: string
  createdAt: string
}

export interface CreateReviewDecisionRequest {
  decision: ReviewDecisionType
  comment?: string
}
