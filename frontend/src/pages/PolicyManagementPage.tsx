import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import { ListChecks } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Surface } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { Select } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows } from '../components/ui/Skeleton'
import { buttonClasses } from '../components/ui/buttonClasses'
import { humanize, severityTone } from '../components/ui/tones'
import { policyConfigurationService } from '../services/policyConfigurationService'
import type { ApiErrorResponse } from '../types/api'
import type { PolicyConfiguration, PolicySeverity } from '../types/policyConfiguration'

const SEVERITY_OPTIONS: PolicySeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

/**
 * Organization-scoped policy configuration. Behavior is unchanged from before
 * the redesign: every role can view; only POLICY_MANAGE can change anything,
 * and the frontend still does not pre-compute who that is - a caller without
 * the permission sees the backend's 403 surfaced as a message, rather than
 * this page duplicating RolePermissions' mapping.
 *
 * What changed is presentation (Product Experience spec, §07): each rule is a
 * readable card stating what it catches and whether it is overridden, instead
 * of a dense six-column table with a severity <select> styled as a badge.
 */
export function PolicyManagementPage() {
  const [policies, setPolicies] = useState<PolicyConfiguration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [savingRuleId, setSavingRuleId] = useState<string | null>(null)

  const loadPolicies = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setPolicies(await policyConfigurationService.list())
    } catch {
      setError('Failed to load policy configuration.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadPolicies()
  }, [loadPolicies])

  async function runUpdate(ruleId: string, action: () => Promise<PolicyConfiguration>) {
    setError(null)
    setSavingRuleId(ruleId)
    try {
      const updated = await action()
      setPolicies((current) => current.map((policy) => (policy.ruleId === ruleId ? updated : policy)))
    } catch (err) {
      setError(describeError(err))
    } finally {
      setSavingRuleId(null)
    }
  }

  const enabledCount = policies.filter((policy) => policy.enabled).length

  return (
    <AppLayout
      eyebrow="Configuration"
      title="Policies"
      description="Enable, disable, or override the severity of each policy rule for your organization. Rule definitions are fixed by GateKeeper; only their configuration is yours."
      actions={
        !isLoading && policies.length > 0 ? (
          <span className="tabular font-mono text-[11px] text-faint">
            {enabledCount} of {policies.length} enabled
          </span>
        ) : undefined
      }
    >
      {error && <ErrorState message={error} onRetry={() => void loadPolicies()} className="mb-5" />}

      {isLoading ? (
        <SkeletonRows rows={4} />
      ) : policies.length === 0 ? (
        <EmptyState
          icon={ListChecks}
          title="No policy rules discovered"
          description="GateKeeper found no policy rules registered in this deployment."
        />
      ) : (
        <div className="flex flex-col gap-3">
          {policies.map((policy) => {
            const isSaving = savingRuleId === policy.ruleId
            return (
              <Surface key={policy.ruleId} padding="p-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm font-semibold text-content">{policy.ruleId}</span>
                      <Chip tone={policy.enabled ? 'pass' : 'neutral'} size="sm">
                        {policy.enabled ? 'Enabled' : 'Disabled'}
                      </Chip>
                      {policy.overridden && (
                        <Chip tone="accent" size="sm">
                          Overridden
                        </Chip>
                      )}
                    </div>
                    <p className="mt-2 text-sm text-muted">{policy.description}</p>
                    <p className="mt-1.5 font-mono text-[11px] text-faint">
                      {humanize(policy.defaultCategory)} · defaults to {policy.defaultSeverity}
                    </p>
                  </div>

                  <div className="flex shrink-0 flex-wrap items-center gap-2">
                    <div className="w-32">
                      <Select
                        aria-label={`Severity for ${policy.ruleId}`}
                        value={policy.severity}
                        disabled={isSaving}
                        onChange={(event) =>
                          void runUpdate(policy.ruleId, () =>
                            policyConfigurationService.update(policy.ruleId, {
                              enabled: policy.enabled,
                              severityOverride: event.target.value as PolicySeverity,
                            }),
                          )
                        }
                      >
                        {SEVERITY_OPTIONS.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </Select>
                    </div>

                    <button
                      type="button"
                      className={buttonClasses('secondary', 'md')}
                      disabled={isSaving}
                      onClick={() =>
                        void runUpdate(policy.ruleId, () =>
                          policyConfigurationService.update(policy.ruleId, {
                            enabled: !policy.enabled,
                            severityOverride: policy.overridden ? policy.severity : undefined,
                          }),
                        )
                      }
                    >
                      {policy.enabled ? 'Disable' : 'Enable'}
                    </button>

                    {policy.overridden && (
                      <button
                        type="button"
                        className={buttonClasses('ghost', 'md')}
                        disabled={isSaving}
                        onClick={() =>
                          void runUpdate(policy.ruleId, () => policyConfigurationService.resetToDefault(policy.ruleId))
                        }
                      >
                        Reset
                      </button>
                    )}
                  </div>
                </div>

                <div className="mt-3 flex items-center gap-2 border-t border-line-soft pt-3">
                  <span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Effective severity</span>
                  <Chip tone={severityTone(policy.severity)} size="sm">
                    {policy.severity}
                  </Chip>
                </div>
              </Surface>
            )
          })}
        </div>
      )}
    </AppLayout>
  )
}

function describeError(err: unknown): string {
  if (axios.isAxiosError<ApiErrorResponse>(err) && err.response?.data?.error) {
    if (err.response.status === 403) {
      return 'You do not have permission to modify policy configuration.'
    }
    return err.response.data.error.message
  }
  return 'Failed to update policy configuration.'
}
