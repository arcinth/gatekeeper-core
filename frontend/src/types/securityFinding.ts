export type SecuritySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type SecurityCategory = 'SECRETS_EXPOSURE' | 'INSECURE_CRYPTOGRAPHY'

export interface SecurityFinding {
  id: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  ruleId: string
  category: SecurityCategory
  severity: SecuritySeverity
  filePath: string
  lineNumber: number
  message: string
  recommendation: string
  createdAt: string
}

export interface SecurityFindingFilters {
  analysisRunId?: number
  repositoryId?: number
  severity?: SecuritySeverity
  category?: SecurityCategory
  ruleId?: string
  page?: number
  size?: number
  sort?: string
}
