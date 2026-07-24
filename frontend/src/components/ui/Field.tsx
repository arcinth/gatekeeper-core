import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

/**
 * Flat, bordered form controls with an accent focus ring. Filters built from
 * these read as a command bar rather than a form (Product Experience spec §09).
 */
const CONTROL =
  'w-full rounded-md border border-line bg-surface-2 px-3 text-sm text-content placeholder:text-faint transition-colors hover:border-faint focus:border-accent disabled:cursor-not-allowed disabled:opacity-50'

export function Label({ htmlFor, children }: { htmlFor?: string; children: ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="mb-1.5 block font-mono text-[10px] uppercase tracking-[0.08em] text-faint">
      {children}
    </label>
  )
}

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`${CONTROL} h-9 ${className}`} {...props} />
}

export function Select({ className = '', children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={`${CONTROL} h-9 ${className}`} {...props}>
      {children}
    </select>
  )
}

export function Textarea({ className = '', ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={`${CONTROL} py-2 ${className}`} {...props} />
}

/** Read-only label/value pair used across detail surfaces. */
export function DataField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">{label}</p>
      <div className="mt-1 truncate text-sm text-content">{value}</div>
    </div>
  )
}
