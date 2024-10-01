"use client";

import { createContext, useCallback, useContext, useRef, useState } from "react";
import { Check } from "lucide-react";

const Ctx = createContext<(message: string) => void>(() => {});

export function useToast() {
  return useContext(Ctx);
}

/** One toast at a time, bottom-left of the content area, as in the console design. Survives client navigation. */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const show = useCallback((m: string) => {
    setMsg(m);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setMsg(null), 3600);
  }, []);
  return (
    <Ctx.Provider value={show}>
      {children}
      {msg && (
        <div role="status" className="fixed bottom-6 left-4 right-4 z-[60] flex items-center gap-2.5 bg-ink px-4 py-3 text-sm text-background shadow-lg lg:left-[264px] lg:right-auto">
          <Check size={16} strokeWidth={2.4} aria-hidden="true" />
          {msg}
        </div>
      )}
    </Ctx.Provider>
  );
}
