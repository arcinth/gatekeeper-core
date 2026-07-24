import { useCallback, useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { UserPlus } from 'lucide-react'
import { SettingsLayout } from '../layouts/SettingsLayout'
import { Surface } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { Input, Label, Select } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows } from '../components/ui/Skeleton'
import { buttonClasses } from '../components/ui/buttonClasses'
import { formatRelativeTime } from '../lib/format'
import { userService } from '../services/userService'
import { roleService } from '../services/roleService'
import { useAuth } from '../hooks/useAuth'
import type { ApiErrorResponse } from '../types/api'
import type { Role } from '../types/role'
import type { User } from '../types/user'

interface UserFormState {
  mode: 'create' | 'edit'
  userId: number | null
  email: string
  password: string
  fullName: string
  roleId: number | ''
  enabled: boolean
}

const EMPTY_FORM: UserFormState = {
  mode: 'create',
  userId: null,
  email: '',
  password: '',
  fullName: '',
  roleId: '',
  enabled: true,
}

/**
 * People and their roles - the former standalone "Users" page, now nested
 * under Settings (Product Experience spec, §05). Behavior is unchanged:
 * create/edit/remove with the same self-removal guard, and the same
 * resolve-roleId-by-name approach, since UserResponse carries only roleName
 * and Role.name is unique.
 */
export function AccessPage() {
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<User[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<UserFormState | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [removingId, setRemovingId] = useState<number | null>(null)

  const loadUsers = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setUsers(await userService.list())
    } catch {
      setError('Failed to load users.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadUsers()
    roleService
      .list()
      .then(setRoles)
      .catch(() => setRoles([]))
  }, [loadUsers])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!form || form.roleId === '') {
      return
    }
    setIsSubmitting(true)
    setError(null)
    try {
      if (form.mode === 'create') {
        await userService.create({
          email: form.email,
          password: form.password,
          fullName: form.fullName,
          roleId: form.roleId,
        })
      } else if (form.userId !== null) {
        await userService.update(form.userId, {
          fullName: form.fullName,
          roleId: form.roleId,
          enabled: form.enabled,
        })
      }
      setForm(null)
      await loadUsers()
    } catch (err) {
      setError(describeError(err, form.mode))
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleRemove(targetUser: User) {
    if (!window.confirm(`Remove ${targetUser.email}? They will no longer be able to access GateKeeper.`)) {
      return
    }
    setError(null)
    setRemovingId(targetUser.id)
    try {
      await userService.remove(targetUser.id)
      setUsers((current) => current.filter((existing) => existing.id !== targetUser.id))
    } catch {
      setError(`Failed to remove ${targetUser.email}. You may not have permission to do this.`)
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <SettingsLayout
      title="Access"
      description="Who can reach GateKeeper, and what each person is allowed to do."
      actions={
        !form && (
          <button type="button" className={buttonClasses('primary', 'md')} onClick={() => setForm({ ...EMPTY_FORM, roleId: roles[0]?.id ?? '' })}>
            <UserPlus className="h-4 w-4" aria-hidden="true" />
            New user
          </button>
        )
      }
    >
      {error && <ErrorState message={error} className="mb-5" />}

      {form && (
        <Surface className="mb-5">
          <form onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-4">
            <h2 className="text-base font-semibold tracking-tight text-content">
              {form.mode === 'create' ? 'New user' : `Edit ${form.email}`}
            </h2>

            <div className="grid gap-4 sm:grid-cols-2">
              {form.mode === 'create' && (
                <>
                  <div>
                    <Label htmlFor="email">Email</Label>
                    <Input
                      id="email"
                      type="email"
                      required
                      value={form.email}
                      onChange={(event) => setForm({ ...form, email: event.target.value })}
                    />
                  </div>
                  <div>
                    <Label htmlFor="password">Password</Label>
                    <Input
                      id="password"
                      type="password"
                      required
                      minLength={8}
                      value={form.password}
                      onChange={(event) => setForm({ ...form, password: event.target.value })}
                    />
                  </div>
                </>
              )}

              <div>
                <Label htmlFor="fullName">Full name</Label>
                <Input
                  id="fullName"
                  type="text"
                  required
                  value={form.fullName}
                  onChange={(event) => setForm({ ...form, fullName: event.target.value })}
                />
              </div>

              <div>
                <Label htmlFor="role">Role</Label>
                <Select
                  id="role"
                  required
                  value={form.roleId}
                  onChange={(event) =>
                    setForm({ ...form, roleId: event.target.value ? Number(event.target.value) : '' })
                  }
                >
                  <option value="">Select a role…</option>
                  {roles.map((role) => (
                    <option key={role.id} value={role.id}>
                      {role.name}
                    </option>
                  ))}
                </Select>
              </div>
            </div>

            {form.mode === 'edit' && (
              <label className="flex items-center gap-2.5 text-sm text-muted">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
                  className="h-4 w-4 rounded border-line accent-[var(--accent)]"
                />
                Account is active
              </label>
            )}

            <div className="flex gap-2">
              <button type="submit" className={buttonClasses('primary', 'md')} disabled={isSubmitting}>
                {isSubmitting ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className={buttonClasses('ghost', 'md')} onClick={() => setForm(null)}>
                Cancel
              </button>
            </div>
          </form>
        </Surface>
      )}

      {isLoading ? (
        <SkeletonRows rows={4} />
      ) : users.length === 0 ? (
        <EmptyState title="No users found" description="Nobody else has access to this GateKeeper organization yet." />
      ) : (
        <div className="divide-y divide-line-soft overflow-hidden rounded-lg border border-line bg-surface">
          {users.map((targetUser) => {
            const isSelf = currentUser?.email.toLowerCase() === targetUser.email.toLowerCase()
            return (
              <div key={targetUser.id} className="flex flex-wrap items-center gap-3 px-4 py-3.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-content">{targetUser.fullName}</p>
                  <p className="truncate font-mono text-[11px] text-faint">{targetUser.email}</p>
                </div>

                <Chip tone="neutral">{targetUser.roleName}</Chip>
                <Chip tone={targetUser.enabled ? 'pass' : 'neutral'}>
                  {targetUser.enabled ? 'Active' : 'Inactive'}
                </Chip>

                <span className="hidden shrink-0 font-mono text-[11px] text-faint lg:block">
                  {formatRelativeTime(targetUser.createdAt)}
                </span>

                <div className="flex shrink-0 gap-1.5">
                  <button
                    type="button"
                    className={buttonClasses('secondary', 'sm')}
                    onClick={() => {
                      setError(null)
                      const matchingRole = roles.find((role) => role.name === targetUser.roleName)
                      setForm({
                        mode: 'edit',
                        userId: targetUser.id,
                        email: targetUser.email,
                        password: '',
                        fullName: targetUser.fullName,
                        roleId: matchingRole?.id ?? '',
                        enabled: targetUser.enabled,
                      })
                    }}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className={buttonClasses('danger', 'sm')}
                    onClick={() => void handleRemove(targetUser)}
                    disabled={isSelf || removingId === targetUser.id}
                    title={isSelf ? 'You cannot remove your own account.' : undefined}
                  >
                    {removingId === targetUser.id ? 'Removing…' : 'Remove'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </SettingsLayout>
  )
}

function describeError(err: unknown, mode: 'create' | 'edit'): string {
  if (axios.isAxiosError<ApiErrorResponse>(err) && err.response?.data?.error) {
    return err.response.data.error.message
  }
  return mode === 'create' ? 'Failed to create user.' : 'Failed to update user.'
}
