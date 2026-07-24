import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { InboxPage } from '../pages/InboxPage'
import { InsightsPage } from '../pages/InsightsPage'
import { PullRequestsPage } from '../pages/PullRequestsPage'
import { PullRequestDetailPage } from '../pages/PullRequestDetailPage'
import { SecurityTriagePage } from '../pages/SecurityTriagePage'
import { RepositoriesPage } from '../pages/RepositoriesPage'
import { RepositoryDetailPage } from '../pages/RepositoryDetailPage'
import { RepositoryConnectCallbackPage } from '../pages/RepositoryConnectCallbackPage'
import { PolicyManagementPage } from '../pages/PolicyManagementPage'
import { AccessPage } from '../pages/AccessPage'
import { AuditLogPage } from '../pages/AuditLogPage'
import { ProtectedRoute } from './ProtectedRoute'

/**
 * Six destinations, down from fifteen (Product Experience spec, §05).
 *
 * Removed entirely: /analysis-runs, /analysis-runs/:id, /policy-findings,
 * /ai-review-runs, /ai-review-runs/:id, /verdicts, /verdicts/:id,
 * /repositories/governance and /dashboard. Every one of them was a facet of a
 * pull request or a repository rather than a place a user would choose to go,
 * and each now lives inside the thing it describes.
 *
 * The old paths redirect rather than 404 so existing bookmarks and any links
 * already posted into GitHub checks still land somewhere sensible.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/inbox"
        element={
          <ProtectedRoute>
            <InboxPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/insights"
        element={
          <ProtectedRoute>
            <InsightsPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/pull-requests"
        element={
          <ProtectedRoute>
            <PullRequestsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/pull-requests/:id"
        element={
          <ProtectedRoute>
            <PullRequestDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/security"
        element={
          <ProtectedRoute>
            <SecurityTriagePage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/repositories"
        element={
          <ProtectedRoute>
            <RepositoriesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/repositories/connect/callback"
        element={
          <ProtectedRoute>
            <RepositoryConnectCallbackPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/repositories/:id"
        element={
          <ProtectedRoute>
            <RepositoryDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/policies"
        element={
          <ProtectedRoute>
            <PolicyManagementPage />
          </ProtectedRoute>
        }
      />

      <Route path="/settings" element={<Navigate to="/settings/access" replace />} />
      <Route
        path="/settings/access"
        element={
          <ProtectedRoute>
            <AccessPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/settings/audit"
        element={
          <ProtectedRoute>
            <AuditLogPage />
          </ProtectedRoute>
        }
      />

      {/* Retired destinations - redirected to wherever their content now lives. */}
      <Route path="/dashboard" element={<Navigate to="/inbox" replace />} />
      <Route path="/analysis-runs" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/analysis-runs/:id" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/policy-findings" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/security-findings" element={<Navigate to="/security" replace />} />
      <Route path="/ai-review-runs" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/ai-review-runs/:id" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/verdicts" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/verdicts/:id" element={<Navigate to="/pull-requests" replace />} />
      <Route path="/repositories/governance" element={<Navigate to="/repositories" replace />} />
      <Route path="/repositories/:id/governance" element={<Navigate to="/repositories" replace />} />
      <Route path="/users" element={<Navigate to="/settings/access" replace />} />
      <Route path="/audit-log" element={<Navigate to="/settings/audit" replace />} />

      <Route path="/" element={<Navigate to="/inbox" replace />} />
      <Route path="*" element={<Navigate to="/inbox" replace />} />
    </Routes>
  )
}
