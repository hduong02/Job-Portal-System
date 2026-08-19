import { Button } from "./ui/button";

export default function PageControls({ page, onPageChange, loading = false }) {
  if (!page || page.totalPages <= 1) return null;

  const current = page.number;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-center gap-3 py-5">
      <Button variant="outline" disabled={loading || current <= 0} onClick={() => onPageChange(current - 1)}>
        Previous
      </Button>
      <span className="text-sm text-slate-600" aria-live="polite">
        Page {current + 1} of {page.totalPages}
      </span>
      <Button variant="outline" disabled={loading || current >= page.totalPages - 1} onClick={() => onPageChange(current + 1)}>
        Next
      </Button>
    </nav>
  );
}
