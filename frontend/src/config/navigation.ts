import {
  FolderGit2,
  GitPullRequest,
  Inbox,
  ListChecks,
  Settings,
  ShieldAlert,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  label: string
  path: string
  icon: LucideIcon
  /** Marks a section boundary above this item in the rail. */
  startsGroup?: boolean
}

/**
 * Six destinations, shaped by workflow rather than by schema (Product
 * Experience spec, §05).
 *
 * The five entity pages that used to sit under an "Analysis" heading -
 * Analysis Runs, Policy Findings, Security Findings, AI Review Runs and
 * Verdicts - are gone as destinations. They were all facets of a single pull
 * request, and they now live inside it. Security keeps a top-level home, but
 * reframed: it is a triage queue of what still needs attention, not a flat
 * dump of every finding ever produced.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Inbox', path: '/inbox', icon: Inbox },
  { label: 'Pull Requests', path: '/pull-requests', icon: GitPullRequest },
  { label: 'Security', path: '/security', icon: ShieldAlert },
  { label: 'Repositories', path: '/repositories', icon: FolderGit2, startsGroup: true },
  { label: 'Policies', path: '/policies', icon: ListChecks },
  { label: 'Insights', path: '/insights', icon: TrendingUp },
  { label: 'Settings', path: '/settings', icon: Settings, startsGroup: true },
]
