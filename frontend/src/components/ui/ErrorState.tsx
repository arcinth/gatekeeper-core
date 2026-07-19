/** Standardizes the red error banner previously written ad-hoc (with inconsistent p-3/p-4) across several pages. */
export function ErrorState({ message }: { message: string }) {
  return <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{message}</div>
}
