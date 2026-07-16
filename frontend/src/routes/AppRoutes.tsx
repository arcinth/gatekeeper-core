import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { DashboardPage } from '../pages/DashboardPage'
import { AnalysisRunsPage } from '../pages/AnalysisRunsPage'
import { AnalysisRunDetailPage } from '../pages/AnalysisRunDetailPage'
import { SecurityFindingsPage } from '../pages/SecurityFindingsPage'
import { AIReviewRunsPage } from '../pages/AIReviewRunsPage'
import { AIReviewRunDetailPage } from '../pages/AIReviewRunDetailPage'
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
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
