export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md'

/**
 * Three tiers plus destructive (Product Experience spec, §09). Primary is the
 * accent and is meant to appear at most once per view; destructive is a red
 * ghost rather than a red slab, so a delete never out-shouts the page's real
 * primary action.
 */
const VARIANT_STYLES: Record<ButtonVariant, string> = {
  primary: 'border border-accent bg-accent text-app hover:bg-accent-hi hover:border-accent-hi font-semibold',
  secondary: 'border border-line bg-surface-2 text-content hover:bg-surface-3 hover:border-faint',
  ghost: 'border border-transparent bg-transparent text-muted hover:bg-surface-2 hover:text-content',
  danger: 'border border-transparent bg-transparent text-block hover:bg-block-bg hover:border-block-line',
}

const SIZE_STYLES: Record<ButtonSize, string> = {
  sm: 'h-7 px-2.5 text-xs',
  md: 'h-9 px-3.5 text-sm',
}

export function buttonClasses(variant: ButtonVariant = 'secondary', size: ButtonSize = 'md', className = ''): string {
  return `inline-flex shrink-0 items-center justify-center gap-1.5 rounded-md transition-colors disabled:cursor-not-allowed disabled:opacity-45 ${VARIANT_STYLES[variant]} ${SIZE_STYLES[size]} ${className}`
}
