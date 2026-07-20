export type GitHubInstallationStatus = 'CONNECTING' | 'ACTIVE' | 'SYNCING' | 'ERROR' | 'DISCONNECTED'

export interface GitHubInstallation {
  id: number
  installationId: number
  githubAccountLogin: string
  githubAccountType: string | null
  repositorySelection: string | null
  status: GitHubInstallationStatus
  lastSuccessfulSyncAt: string | null
  lastSyncError: string | null
  active: boolean
  repositoryCount: number
  createdAt: string
}

export interface InstallUrl {
  url: string | null
  appConfigured: boolean
}
