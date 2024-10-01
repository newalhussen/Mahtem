"use client";

import { useEffect, useRef, useState } from "react";
import { Certificate, type CertificateProps } from "./certificate";

/** Scales the certificate down to the width of its container (never up past maxWidth), so it is never clipped. */
export function FitCertificate({ maxWidth = 560, ...props }: Omit<CertificateProps, "w"> & { maxWidth?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const [w, setW] = useState(maxWidth);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setW(Math.max(260, Math.min(maxWidth, Math.floor(el.clientWidth))));
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [maxWidth]);
  return (
    <div ref={ref} className="w-full" style={{ maxWidth }}>
      <div className="shadow-md" style={{ width: w }}>
        <Certificate {...props} w={w} />
      </div>
    </div>
  );
}
