import { useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { userService } from '../services/userService'
import { roleService } from '../services/roleService'
import { useAuth } from '../hooks/useAuth'
import type { ApiErrorResponse } from '../types/api'
import type { Role } from '../types/role'
import type { User } from '../types/user'

// No dedicated Roles management page: RoleController's create/update/delete
// exist on the backend, but the backlog story driving this page ("Role
// Visibility") only asks to see which role a user holds, not to manage the
// role catalog itself - satisfied here by the Role column plus the
// assignment dropdown below, both fed by roleService.list(). See
// roleService's own comment for the same reasoning.
const STATUS_STYLES: Record<'active' | 'inactive', string> = {
  active: 'bg-emerald-100 text-emerald-800',
  inactive: 'bg-slate-100 text-slate-600',
}

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
    <AppLayout>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Users</h1>
        {!form && (
          <button
            onClick={openCreateForm}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800"
          >
            New User
          </button>
        )}
      </div>

      {error && <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>}

      {form && (
        <form
          onSubmit={handleSubmit}
          className="mb-6 flex max-w-md flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
        >
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
                className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
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
                className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
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
              className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
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
              className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
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
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {isSubmitting ? 'Saving...' : 'Save'}
            </button>
            <button
              type="button"
              onClick={closeForm}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Email</th>
                <th className="px-4 py-2">Full Name</th>
                <th className="px-4 py-2">Role</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Created</th>
                <th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {users.length ? (
                users.map((targetUser) => {
                  const isSelf = currentUser?.email.toLowerCase() === targetUser.email.toLowerCase()
                  return (
                    <tr key={targetUser.id} className="hover:bg-slate-50">
                      <td className="px-4 py-2 font-medium text-slate-900">{targetUser.email}</td>
                      <td className="px-4 py-2 text-slate-700">{targetUser.fullName}</td>
                      <td className="px-4 py-2">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                          {targetUser.roleName}
                        </span>
                      </td>
                      <td className="px-4 py-2">
                        <span
                          className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                            targetUser.enabled ? STATUS_STYLES.active : STATUS_STYLES.inactive
                          }`}
                        >
                          {targetUser.enabled ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-2 text-slate-500">{new Date(targetUser.createdAt).toLocaleString()}</td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex justify-end gap-2">
                          <button
                            onClick={() => openEditForm(targetUser)}
                            className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => void handleRemove(targetUser)}
                            disabled={isSelf || removingId === targetUser.id}
                            title={isSelf ? 'You cannot remove your own account.' : undefined}
                            className="rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
                          >
                            {removingId === targetUser.id ? 'Removing...' : 'Remove'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })
              ) : (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                    No users found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
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
