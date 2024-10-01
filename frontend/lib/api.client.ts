"use client";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public fields?: Record<string, string>,
  ) {
    super(message);
  }
}

/** Browser call to the API through the same-origin /api proxy. The session cookie is httpOnly, so it is never visible to scripts. */
export async function api<T = unknown>(path: string, init: { method?: string; body?: unknown; form?: FormData } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  let body: BodyInit | undefined;
  if (init.form) {
    body = init.form;
  } else if (init.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(init.body);
  }
  let res: Response;
  try {
    res = await fetch(`/api${path}`, { method: init.method ?? (body ? "POST" : "GET"), headers, body, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "Could not reach Mahtem. Check your connection and try again.");
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let json: unknown = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* not JSON */
  }
  if (!res.ok) {
    const b = (json ?? {}) as { error?: string; code?: string; fields?: Record<string, string> };
    if (res.status === 401 && !path.startsWith("/auth/")) {
      window.location.href = "/login";
    }
    throw new ApiError(res.status, b.error ?? `Something went wrong (${res.status}).`, b.code, b.fields);
  }
  return json as T;
}
