import type { Config } from "./config";

/**
 * The headers that protect every page this server renders (ADR 0063). They are set here, in the server, and not only at a
 * reverse proxy, so the application is protected behind whatever front it is deployed behind.
 */

/** A new nonce for one response: a script or style the server renders carries it, and nothing injected into the page does. */
export function newNonce(): string {
  return Buffer.from(crypto.randomUUID()).toString("base64");
}

/**
 * The Content-Security-Policy of a page. Scripts run only with the page's nonce (and what such a script loads);
 * everything else is limited to this origin. Styles keep `'unsafe-inline'` because components position and size elements
 * with inline `style` attributes, which a nonce cannot cover; moving them to classes would let it go.
 */
export function contentSecurityPolicy(nonce: string, options: { https: boolean; development: boolean }): string {
  const directives = [
    "default-src 'self'",
    // React's development builds need eval to rebuild stack traces; production never does.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${options.development ? " 'unsafe-eval'" : ""}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ];
  if (options.https) directives.push("upgrade-insecure-requests");
  return directives.join("; ");
}

export const CSP_HEADER = "Content-Security-Policy";
export const CSP_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

export interface SecurityHeaders {
  /** Set on the response. */
  response: Record<string, string>;
  /** Set on the request that is passed on, because Next.js reads the nonce from the policy there. */
  request: Record<string, string>;
}

/**
 * The headers for a page response. The policy starts in report-only mode, so what it would block shows in the browser's
 * console without breaking anything, and is switched to `enforce` once a release has shown nothing is blocked wrongly.
 * HSTS is sent only over HTTPS: sent over HTTP it would be ignored, and it must never be sent for a server that is not
 * meant to be reached over HTTPS.
 */
export function securityHeaders(
  nonce: string,
  config: Pick<Config, "publicBaseUrl" | "cspMode">,
  development: boolean,
): SecurityHeaders {
  const https = config.publicBaseUrl.startsWith("https://");
  const response: Record<string, string> = { "Cross-Origin-Opener-Policy": "same-origin" };
  const request: Record<string, string> = { "x-nonce": nonce };
  if (https) response["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains";
  if (config.cspMode !== "off") {
    const name = config.cspMode === "enforce" ? CSP_HEADER : CSP_REPORT_ONLY_HEADER;
    const policy = contentSecurityPolicy(nonce, { https, development });
    response[name] = policy;
    request[name] = policy;
  }
  return { response, request };
}
