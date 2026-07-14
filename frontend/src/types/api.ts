export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface ApiError {
  code: string
  message: string
}

export interface ApiErrorResponse {
  success: false
  error: ApiError
}
