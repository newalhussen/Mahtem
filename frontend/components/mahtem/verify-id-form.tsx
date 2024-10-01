"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

/** Normalises a typed or pasted ID (or a full verification link) and opens its public verification page. */
export function extractId(raw: string): string {
  const t = raw.trim();
  const m = t.match(/MHT-[A-Z0-9]+-\d{2}-[A-Z0-9]{4}-[A-Z0-9]{4}/i);
  return (m ? m[0] : t).toUpperCase().replace(/\s+/g, "");
}

export function VerifyIdForm({
  id,
  placeholder = "MHT-AAU-26-K7Q4-8TZ2",
  label,
  buttonClass = "btn btn-secondary",
  inputStyle,
  className,
  initial = "",
  cta = "Verify",
  onDarkHeader,
}: {
  id: string;
  placeholder?: string;
  label?: string;
  buttonClass?: string;
  inputStyle?: React.CSSProperties;
  className?: string;
  initial?: string;
  cta?: string;
  onDarkHeader?: boolean;
}) {
  const router = useRouter();
  const [value, setValue] = useState(initial);
  const go = (e: React.FormEvent) => {
    e.preventDefault();
    const v = extractId(value);
    if (v) router.push(`/verify/${encodeURIComponent(v)}`);
  };
  return (
    <form onSubmit={go} className={className} style={{ display: "flex", gap: 8, flexDirection: "column" }} data-dark={onDarkHeader}>
      {label && (
        <label htmlFor={id} style={{ fontSize: 13, fontWeight: 600 }}>
          {label}
        </label>
      )}
      <div style={{ display: "flex", gap: 8 }}>
        <input
          id={id}
          className="input mono"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={placeholder}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          style={{ minHeight: 44, ...inputStyle }}
        />
        <button type="submit" className={buttonClass} style={{ minHeight: 44 }}>
          {cta}
        </button>
      </div>
    </form>
  );
}
