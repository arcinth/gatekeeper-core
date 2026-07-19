import { useEffect, useState } from 'react'
import axios from 'axios'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { SEVERITY_TONES } from '../components/ui/badgeTones'
import { policyConfigurationService } from '../services/policyConfigurationService'
import type { ApiErrorResponse } from '../types/api'
import type { PolicyConfiguration, PolicySeverity } from '../types/policyConfiguration'

const SEVERITY_OPTIONS: PolicySeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

/**
 * Organization-scoped policy configuration (Milestone 6). Every role can see
 * this page (WORKSPACE_READ); only POLICY_MANAGE can change anything - the
 * frontend does not pre-compute who that is (permissions aren't exposed to
 * the client, and duplicating RolePermissions' mapping here would be exactly
 * the kind of role-name duplication Milestone 5/6 exist to prevent). A
 * caller without POLICY_MANAGE simply sees a 403 surfaced as an error
 * message when they attempt a change - the same posture already agreed for
 * Milestone 5's own review-decision form.
 */
export function PolicyManagementPage() {
  const [policies, setPolicies] = useState<PolicyConfiguration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [savingRuleId, setSavingRuleId] = useState<string | null>(null)

  useEffect(() => {
    loadPolicies()
  }, [])

  function loadPolicies() {
    setIsLoading(true)
    setError(null)
    policyConfigurationService
      .list()
      .then(setPolicies)
      .catch(() => setError('Failed to load policy configuration.'))
      .finally(() => setIsLoading(false))
  }

  async function toggleEnabled(policy: PolicyConfiguration) {
    await runUpdate(policy.ruleId, () =>
      policyConfigurationService.update(policy.ruleId, {
        enabled: !policy.enabled,
        severityOverride: policy.overridden ? policy.severity : undefined,
      }),
    )
  }

  async function changeSeverity(policy: PolicyConfiguration, severity: PolicySeverity) {
    await runUpdate(policy.ruleId, () =>
      policyConfigurationService.update(policy.ruleId, { enabled: policy.enabled, severityOverride: severity }),
    )
  }

  async function resetToDefault(policy: PolicyConfiguration) {
    await runUpdate(policy.ruleId, () => policyConfigurationService.resetToDefault(policy.ruleId))
  }

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

  return (
    <AppLayout
      title="Policy Management"
      description="Enable, disable, or override the severity of each policy rule for your organization. Rule definitions themselves are fixed by GateKeeper; only their configuration is organization-specific."
    >
      {error && <ErrorState message={error} />}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <Table>
          <TableHead>
            <tr>
              <th className="px-4 py-2">Rule</th>
              <th className="px-4 py-2">Category</th>
              <th className="px-4 py-2">Description</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Severity</th>
              <th className="px-4 py-2"></th>
            </tr>
          </TableHead>
          <TableBody>
            {policies.length ? (
              policies.map((policy) => (
                <tr key={policy.ruleId} className="hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium text-slate-900">{policy.ruleId}</td>
                  <td className="px-4 py-2 text-slate-500">{policy.defaultCategory}</td>
                  <td className="px-4 py-2 text-slate-700">{policy.description}</td>
                  <td className="px-4 py-2">
                    <Badge tone={policy.enabled ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'}>
                      {policy.enabled ? 'Enabled' : 'Disabled'}
                    </Badge>
                  </td>
                  <td className="px-4 py-2">
                    <select
                      value={policy.severity}
                      disabled={savingRuleId === policy.ruleId}
                      onChange={(event) => void changeSeverity(policy, event.target.value as PolicySeverity)}
                      className={`rounded-full border-0 px-2 py-0.5 text-xs font-medium ${SEVERITY_TONES[policy.severity]}`}
                    >
                      {SEVERITY_OPTIONS.map((option) => (
                        <option key={option} value={option}>
                          {option}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="px-4 py-2 text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        disabled={savingRuleId === policy.ruleId}
                        onClick={() => void toggleEnabled(policy)}
                      >
                        {policy.enabled ? 'Disable' : 'Enable'}
                      </Button>
                      {policy.overridden && (
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled={savingRuleId === policy.ruleId}
                          onClick={() => void resetToDefault(policy)}
                        >
                          Reset to default
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            ) : (
              <EmptyTableRow colSpan={6}>No policy rules discovered.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
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
