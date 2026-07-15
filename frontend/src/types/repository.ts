export interface Repository {
  id: number
  name: string
  fullName: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}
