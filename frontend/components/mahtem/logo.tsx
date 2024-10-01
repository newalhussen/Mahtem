export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="flex-none">
      <rect width="32" height="32" fill="#7c1405" />
      <rect x="4.5" y="4.5" width="23" height="23" fill="none" stroke="#f8f4f4" strokeWidth="1.4" strokeDasharray="1.6 1.6" />
      <path d="M10 22V10.5l6 6.5 6-6.5V22" fill="none" stroke="#f8f4f4" strokeWidth="2.6" />
    </svg>
  );
}

export function Wordmark({ size = 28, text = 18 }: { size?: number; text?: number }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <LogoMark size={size} />
      <span className="font-extrabold tracking-[-0.01em]" style={{ fontSize: text }}>
        Mahtem
      </span>
    </span>
  );
}
