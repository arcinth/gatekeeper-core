export function LoadingSpinner() {
  return (
    <div className="flex h-full w-full items-center justify-center py-12">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-slate-700" />
    </div>
  )
}
