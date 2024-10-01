import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import type { Me } from "./types";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

export class ApiFailure extends Error {
  constructor(public status: number, message: string, public code?: string) {
    super(message);
  }
}

/** Server-side call to the Spring Boot API, carrying the visitor's session cookie and client IP. */
export async function apiGet<T>(path: string, opts: { auth?: boolean } = {}): Promise<T> {
  const h = await headers();
  const forwarded: Record<string, string> = {};
  const xff = h.get("x-forwarded-for");
  if (xff) forwarded["X-Forwarded-For"] = xff;
  for (const k of ["cf-ipcountry", "x-vercel-ip-country", "x-vercel-ip-city"]) {
    const v = h.get(k);
    if (v) forwarded[k] = v;
  }
  if (opts.auth !== false) {
    const jar = await cookies();
    const session = jar.get("mahtem_session");
    if (session) forwarded["Cookie"] = `mahtem_session=${session.value}`;
  }
  const res = await fetch(`${BACKEND}${path}`, { headers: forwarded, cache: "no-store" });
  if (res.status === 401) redirect("/login");
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    let code: string | undefined;
    try {
      const body = await res.json();
      message = body.error ?? message;
      code = body.code;
    } catch {
      /* not JSON */
    }
    throw new ApiFailure(res.status, message, code);
  }
  return (await res.json()) as T;
}

/** The signed-in user, or null for visitors. */
export async function currentUser(): Promise<Me | null> {
  const body = await apiGet<{ user: Me | null }>("/api/auth/me");
  return body.user;
}

/** For console pages: returns the user or sends the visitor to the sign-in page. */
export async function requireUser(): Promise<Me> {
  const me = await currentUser();
  if (!me) redirect("/login");
  return me;
}
