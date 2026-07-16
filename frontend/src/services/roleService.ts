import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { Role } from '../types/role'

// list() only - RoleController's full create/update/delete CRUD is not
// exercised by any page. The only backlog story driving this milestone
// ("Role Visibility") is about seeing which role a *user* holds, not
// managing the catalog of roles itself - satisfied by UsersPage showing
// each user's roleName plus this list feeding the role-assignment dropdown.
// A standalone Roles management page was deliberately not built - see
// UsersPage's own module comment.
export const roleService = {
  async list(): Promise<Role[]> {
    const response = await apiClient.get<ApiResponse<Role[]>>('/roles')
    return response.data.data
  },
}
