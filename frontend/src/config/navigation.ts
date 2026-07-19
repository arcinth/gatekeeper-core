import {
  Activity,
  ClipboardList,
  FolderGit2,
  Gavel,
  GitPullRequest,
  LayoutDashboard,
  ListChecks,
  ShieldAlert,
  Sparkles,
  Users,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  label: string
  path: string
  icon: LucideIcon
}

export interface NavSection {
  label: string
  items: NavItem[]
}

/**
 * Single source of truth for the sidebar. Grouped to match how the product
 * is actually used: the reviewer workspace first (Pull Requests, the primary
 * screen per Milestone 1), then the engines that feed it, then
 * administration. Repository Governance isn't listed here - it's a drill-down
 * reached from a specific Repository row, not a top-level destination.
 */
export const NAV_SECTIONS: NavSection[] = [
  {
    label: 'Overview',
    items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard }],
  },
  {
    label: 'Reviewer Workspace',
    items: [{ label: 'Pull Requests', path: '/pull-requests', icon: GitPullRequest }],
  },
  {
    label: 'Analysis',
    items: [
      { label: 'Analysis Runs', path: '/analysis-runs', icon: Activity },
      { label: 'Policy Findings', path: '/policy-findings', icon: ClipboardList },
      { label: 'Security Findings', path: '/security-findings', icon: ShieldAlert },
      { label: 'AI Review Runs', path: '/ai-review-runs', icon: Sparkles },
      { label: 'Verdicts', path: '/verdicts', icon: Gavel },
    ],
  },
  {
    label: 'Administration',
    items: [
      { label: 'Repositories', path: '/repositories', icon: FolderGit2 },
      { label: 'Policies', path: '/policies', icon: ListChecks },
      { label: 'Users', path: '/users', icon: Users },
    ],
  },
]
