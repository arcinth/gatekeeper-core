export type PolicySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type PolicyCategory = 'MAINTAINABILITY' | 'CODE_QUALITY'

export interface PolicyFinding {
  id: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  ruleId: string
  category: PolicyCategory
  severity: PolicySeverity
  filePath: string
  lineNumber: number
  message: string
  recommendation: string
  createdAt: string
}

export interface PolicyFindingFilters {
  analysisRunId?: number
  repositoryId?: number
  severity?: PolicySeverity
  category?: PolicyCategory
  ruleId?: string
  page?: number
  size?: number
  sort?: string
}
