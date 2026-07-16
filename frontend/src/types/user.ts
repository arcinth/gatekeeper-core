// Field-for-field mirror of the backend's UserResponse/CreateUserRequest/
// UpdateUserRequest. UpdateUserRequest deliberately has no email field - the
// backend never supports changing a user's email after creation, only
// fullName/roleId/enabled (see UserService.update) - so there is no
// "change email" capability to build here either.
export interface User {
  id: number
  email: string
  fullName: string
  roleName: string
  enabled: boolean
  createdAt: string
}

export interface CreateUserRequest {
  email: string
  password: string
  fullName: string
  roleId: number
}

export interface UpdateUserRequest {
  fullName: string
  roleId: number
  enabled: boolean
}
