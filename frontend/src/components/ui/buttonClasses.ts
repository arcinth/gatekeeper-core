export type ButtonVariant = 'primary' | 'secondary' | 'danger'
export type ButtonSize = 'sm' | 'md'

const VARIANT_STYLES: Record<ButtonVariant, string> = {
  primary: 'border border-slate-900 bg-slate-900 text-white hover:bg-slate-800',
  secondary: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100',
  danger: 'border border-red-300 bg-white text-red-700 hover:bg-red-50',
}

const SIZE_STYLES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1 text-xs',
  md: 'px-3 py-1.5 text-sm',
}

/**
 * Standardizes button styling across the app - previously "primary" actions
 * varied between bg-slate-900 and bg-slate-800 depending on which page wrote
 * them. Exported as a plain classnames function too, so the same visual
 * language applies to non-<button> elements (e.g. an <a> styled as a button)
 * without duplicating the variant/size logic.
 */
export function buttonClasses(variant: ButtonVariant = 'secondary', size: ButtonSize = 'md', className = ''): string {
  return `inline-flex items-center justify-center gap-1.5 rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT_STYLES[variant]} ${SIZE_STYLES[size]} ${className}`
}
