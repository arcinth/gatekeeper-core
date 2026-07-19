import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { DashboardPage } from '../pages/DashboardPage'
import { PullRequestsPage } from '../pages/PullRequestsPage'
import { PullRequestDetailPage } from '../pages/PullRequestDetailPage'
import { AnalysisRunsPage } from '../pages/AnalysisRunsPage'
import { AnalysisRunDetailPage } from '../pages/AnalysisRunDetailPage'
import { PolicyFindingsPage } from '../pages/PolicyFindingsPage'
import { SecurityFindingsPage } from '../pages/SecurityFindingsPage'
import { AIReviewRunsPage } from '../pages/AIReviewRunsPage'
import { AIReviewRunDetailPage } from '../pages/AIReviewRunDetailPage'
import { VerdictsPage } from '../pages/VerdictsPage'
import { VerdictDetailPage } from '../pages/VerdictDetailPage'
import { RepositoryGovernancePage } from '../pages/RepositoryGovernancePage'
import { RepositoriesPage } from '../pages/RepositoriesPage'
import { PolicyManagementPage } from '../pages/PolicyManagementPage'
import { UsersPage } from '../pages/UsersPage'
import { AuditLogPage } from '../pages/AuditLogPage'
import { ProtectedRoute } from './ProtectedRoute'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
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
        path="/analysis-runs"
        element={
          <ProtectedRoute>
            <AnalysisRunsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/analysis-runs/:id"
        element={
          <ProtectedRoute>
            <AnalysisRunDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/policy-findings"
        element={
          <ProtectedRoute>
            <PolicyFindingsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/security-findings"
        element={
          <ProtectedRoute>
            <SecurityFindingsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/ai-review-runs"
        element={
          <ProtectedRoute>
            <AIReviewRunsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/ai-review-runs/:id"
        element={
          <ProtectedRoute>
            <AIReviewRunDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/verdicts"
        element={
          <ProtectedRoute>
            <VerdictsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/verdicts/:id"
        element={
          <ProtectedRoute>
            <VerdictDetailPage />
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
        path="/repositories/governance"
        element={
          <ProtectedRoute>
            <RepositoryGovernancePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/repositories/:id/governance"
        element={
          <ProtectedRoute>
            <RepositoryGovernancePage />
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
      <Route
        path="/users"
        element={
          <ProtectedRoute>
            <UsersPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/audit-log"
        element={
          <ProtectedRoute>
            <AuditLogPage />
          </ProtectedRoute>
        }
      />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
