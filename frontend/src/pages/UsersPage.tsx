import { useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { ACTIVE_STATE_TONES } from '../components/ui/badgeTones'
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

export function UsersPage() {
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<User[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<UserFormState | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [removingId, setRemovingId] = useState<number | null>(null)

  useEffect(() => {
    loadUsers()
    roleService.list().then(setRoles).catch(() => setRoles([]))
  }, [])

  function loadUsers() {
    setIsLoading(true)
    setError(null)
    userService
      .list()
      .then(setUsers)
      .catch(() => setError('Failed to load users.'))
      .finally(() => setIsLoading(false))
  }

  function openCreateForm() {
    setError(null)
    setForm({ ...EMPTY_FORM, roleId: roles[0]?.id ?? '' })
  }

  function openEditForm(targetUser: User) {
    setError(null)
    // UserResponse only carries roleName, not roleId (Role Visibility's own
    // story is about display, not editing, so the backend never needed to
    // expose it) - the assignment dropdown resolves it by matching name,
    // safe because Role.name carries its own UNIQUE constraint.
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
  }

  function closeForm() {
    setForm(null)
  }

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
      loadUsers()
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
    <AppLayout
      title="Users"
      actions={
        !form && (
          <Button variant="primary" onClick={openCreateForm}>
            New User
          </Button>
        )
      }
    >
      {error && <ErrorState message={error} />}

      {form && (
        <Card className="mb-6 max-w-md">
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <h2 className="text-sm font-semibold text-slate-900">
              {form.mode === 'create' ? 'New User' : `Edit ${form.email}`}
            </h2>

            {form.mode === 'create' && (
              <div className="flex flex-col gap-1">
                <label htmlFor="email" className="text-sm font-medium text-slate-700">
                  Email
                </label>
                <input
                  id="email"
                  type="email"
                  required
                  value={form.email}
                  onChange={(event) => setForm({ ...form, email: event.target.value })}
                  className="rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}

            {form.mode === 'create' && (
              <div className="flex flex-col gap-1">
                <label htmlFor="password" className="text-sm font-medium text-slate-700">
                  Password
                </label>
                <input
                  id="password"
                  type="password"
                  required
                  minLength={8}
                  value={form.password}
                  onChange={(event) => setForm({ ...form, password: event.target.value })}
                  className="rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}

            <div className="flex flex-col gap-1">
              <label htmlFor="fullName" className="text-sm font-medium text-slate-700">
                Full Name
              </label>
              <input
                id="fullName"
                type="text"
                required
                value={form.fullName}
                onChange={(event) => setForm({ ...form, fullName: event.target.value })}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="role" className="text-sm font-medium text-slate-700">
                Role
              </label>
              <select
                id="role"
                required
                value={form.roleId}
                onChange={(event) => setForm({ ...form, roleId: event.target.value ? Number(event.target.value) : '' })}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select a role...</option>
                {roles.map((role) => (
                  <option key={role.id} value={role.id}>
                    {role.name}
                  </option>
                ))}
              </select>
            </div>

            {form.mode === 'edit' && (
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
                />
                Active
              </label>
            )}

            <div className="flex gap-2">
              <Button type="submit" variant="primary" disabled={isSubmitting}>
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
              <Button type="button" variant="secondary" onClick={closeForm}>
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <Table>
          <TableHead>
            <tr>
              <th className="px-4 py-2">Email</th>
              <th className="px-4 py-2">Full Name</th>
              <th className="px-4 py-2">Role</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Created</th>
              <th className="px-4 py-2"></th>
            </tr>
          </TableHead>
          <TableBody>
            {users.length ? (
              users.map((targetUser) => {
                const isSelf = currentUser?.email.toLowerCase() === targetUser.email.toLowerCase()
                return (
                  <tr key={targetUser.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2 font-medium text-slate-900">{targetUser.email}</td>
                    <td className="px-4 py-2 text-slate-700">{targetUser.fullName}</td>
                    <td className="px-4 py-2">
                      <Badge tone="bg-slate-100 text-slate-700">{targetUser.roleName}</Badge>
                    </td>
                    <td className="px-4 py-2">
                      <Badge tone={targetUser.enabled ? ACTIVE_STATE_TONES.active : ACTIVE_STATE_TONES.inactive}>
                        {targetUser.enabled ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                    <td className="px-4 py-2 text-slate-500">{new Date(targetUser.createdAt).toLocaleString()}</td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variant="secondary" size="sm" onClick={() => openEditForm(targetUser)}>
                          Edit
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => void handleRemove(targetUser)}
                          disabled={isSelf || removingId === targetUser.id}
                          title={isSelf ? 'You cannot remove your own account.' : undefined}
                        >
                          {removingId === targetUser.id ? 'Removing...' : 'Remove'}
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })
            ) : (
              <EmptyTableRow colSpan={6}>No users found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}

function describeError(err: unknown, mode: 'create' | 'edit'): string {
  if (axios.isAxiosError<ApiErrorResponse>(err) && err.response?.data?.error) {
    return err.response.data.error.message
  }
  return mode === 'create' ? 'Failed to create user.' : 'Failed to update user.'
}
