import { NextResponse, type NextRequest } from "next/server";

import { defaultDeps } from "@/lib/bff/deps";
import { currentSession } from "@/lib/bff/refresh";
import { parseCookies } from "@/lib/cookies";
import { newNonce, securityHeaders } from "@/lib/security-headers";
import { cookieNames } from "@/lib/session";
import { isModulePath } from "@/modules";

/**
 * Runs before page rendering. Server Components cannot set cookies, so a token that needs refreshing is refreshed
 * here, where the new session can be written to the response and handed on to the page that renders next (ADR 0026).
 * Pages that need a signed-in user send everyone else to the sign-in route. Every page also gets the security headers
 * and a nonce for its scripts (ADR 0063).
 */
function needsSignIn(pathname: string): boolean {
  return (
    pathname === "/me" ||
    pathname === "/profile" ||
    pathname === "/admin" ||
    pathname.startsWith("/admin/") ||
    isModulePath(pathname)
  );
}

/** Replaces the session chunks in a Cookie header with the ones just written, so the page sees the new session. */
function withNewSession(cookieHeader: string | null, setCookies: string[], sessionCookie: string): string {
  const cookies = parseCookies(cookieHeader);
  for (const name of [...cookies.keys()]) {
    if (name.startsWith(`${sessionCookie}.`)) cookies.delete(name);
  }
  for (const setCookie of setCookies) {
    const pair = setCookie.split(";")[0] ?? "";
    const index = pair.indexOf("=");
    const name = pair.slice(0, index);
    const value = pair.slice(index + 1);
    if (value && !/Max-Age=0/i.test(setCookie)) cookies.set(name, value);
  }
  return [...cookies].map(([name, value]) => `${name}=${value}`).join("; ");
}

export async function proxy(request: NextRequest) {
  const deps = defaultDeps();
  const cookieHeader = request.headers.get("cookie");
  const result = await currentSession(cookieHeader, deps);
  const signInRequired = needsSignIn(request.nextUrl.pathname);
  const secure = deps.config.publicBaseUrl.startsWith("https://");

  const nonce = newNonce();
  const headers = securityHeaders(nonce, deps.config, process.env.NODE_ENV !== "production");
  const requestHeaders = new Headers(request.headers);
  for (const [name, value] of Object.entries(headers.request)) requestHeaders.set(name, value);

  /** The response, with the security headers and the cookies of a session that was just refreshed or cleared. */
  const finish = (response: NextResponse): NextResponse => {
    for (const [name, value] of Object.entries(headers.response)) response.headers.set(name, value);
    for (const cookie of result.setCookies) response.headers.append("Set-Cookie", cookie);
    return response;
  };

  if (result.status !== "ok") {
    if (!signInRequired) return finish(NextResponse.next({ request: { headers: requestHeaders } }));
    const login = new URL("/auth/login", deps.config.publicBaseUrl);
    login.searchParams.set("returnTo", request.nextUrl.pathname);
    return finish(NextResponse.redirect(login));
  }

  if (result.setCookies.length > 0) {
    requestHeaders.set("cookie", withNewSession(cookieHeader, result.setCookies, cookieNames(secure).session));
  }
  return finish(NextResponse.next({ request: { headers: requestHeaders } }));
}

// Every page, so that every page has a nonce and the security headers. Left out: the BFF's own routes (`/auth/...`),
// which answer with redirects and JSON, the API proxy (`/api/...`), and the files Next.js serves itself. The slash after
// `api` and `auth` matters: without it a page called `/authors` would be left out too. The paths that need a signed-in user are not
// listed here; `needsSignIn` decides, and a feature module's paths come from `modules.ts`.
export const config = {
  matcher: ["/((?!api/|auth/|_next/static|_next/image|favicon.ico|healthz).*)"],
};
