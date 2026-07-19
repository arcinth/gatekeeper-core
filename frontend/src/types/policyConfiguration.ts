export type PolicyCategory = 'MAINTAINABILITY' | 'CODE_QUALITY'
export type PolicySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface PolicyConfiguration {
  ruleId: string
  description: string
  defaultCategory: PolicyCategory
  defaultSeverity: PolicySeverity
  enabled: boolean
  severity: PolicySeverity
  overridden: boolean
}

export interface UpdatePolicyConfigurationRequest {
  enabled: boolean
  severityOverride?: PolicySeverity | null
}
