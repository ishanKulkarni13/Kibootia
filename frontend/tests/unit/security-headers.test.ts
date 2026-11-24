import { describe, expect, it } from "vitest";

import { contentSecurityPolicy, CSP_HEADER, CSP_REPORT_ONLY_HEADER, newNonce, securityHeaders } from "@/lib/security-headers";

const http = { publicBaseUrl: "http://localhost:3470", cspMode: "report-only" } as const;
const https = { publicBaseUrl: "https://kibootia.example", cspMode: "report-only" } as const;

describe("content security policy (ADR 0063)", () => {
  it("lets scripts run only with the page's nonce and forbids plugins, framing and a changed base", () => {
    const policy = contentSecurityPolicy("abc123", { https: true, development: false });
    expect(policy).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(policy).not.toContain("'unsafe-eval'");
    expect(policy).toContain("default-src 'self'");
    expect(policy).toContain("object-src 'none'");
    expect(policy).toContain("base-uri 'self'");
    expect(policy).toContain("frame-ancestors 'none'");
    expect(policy).toContain("form-action 'self'");
  });

  it("does not allow arbitrary inline or remote scripts", () => {
    const script = contentSecurityPolicy("n", { https: true, development: false })
      .split("; ")
      .find((directive) => directive.startsWith("script-src"));
    expect(script).toBeDefined();
    expect(script).not.toContain("'unsafe-inline'");
    expect(script).not.toContain("http:");
    expect(script).not.toContain("*");
  });

  it("allows eval only while developing", () => {
    expect(contentSecurityPolicy("n", { https: false, development: true })).toContain("'unsafe-eval'");
    expect(contentSecurityPolicy("n", { https: false, development: false })).not.toContain("'unsafe-eval'");
  });

  it("upgrades insecure requests only over HTTPS", () => {
    expect(contentSecurityPolicy("n", { https: true, development: false })).toContain("upgrade-insecure-requests");
    expect(contentSecurityPolicy("n", { https: false, development: false })).not.toContain("upgrade-insecure-requests");
  });

  it("makes a different nonce each time, long enough to be unguessable", () => {
    const nonces = new Set(Array.from({ length: 50 }, () => newNonce()));
    expect(nonces.size).toBe(50);
    for (const nonce of nonces) expect(nonce.length).toBeGreaterThanOrEqual(32);
  });
});

describe("security headers of a page (ADR 0063)", () => {
  it("starts in report-only mode: the policy is reported, not enforced", () => {
    const { response, request } = securityHeaders("n1", http, false);
    expect(response[CSP_REPORT_ONLY_HEADER]).toContain("'nonce-n1'");
    expect(response[CSP_HEADER]).toBeUndefined();
    // Next.js reads the nonce from the policy on the request that is passed on.
    expect(request[CSP_REPORT_ONLY_HEADER]).toBe(response[CSP_REPORT_ONLY_HEADER]);
    expect(request["x-nonce"]).toBe("n1");
  });

  it("enforces the policy when asked to", () => {
    const { response } = securityHeaders("n2", { ...http, cspMode: "enforce" }, false);
    expect(response[CSP_HEADER]).toContain("'nonce-n2'");
    expect(response[CSP_REPORT_ONLY_HEADER]).toBeUndefined();
  });

  it("sends no policy when it is off, and still protects against window access from other origins", () => {
    const { response, request } = securityHeaders("n3", { ...http, cspMode: "off" }, false);
    expect(response[CSP_HEADER]).toBeUndefined();
    expect(response[CSP_REPORT_ONLY_HEADER]).toBeUndefined();
    expect(request[CSP_HEADER]).toBeUndefined();
    expect(response["Cross-Origin-Opener-Policy"]).toBe("same-origin");
  });

  it("sends HSTS over HTTPS and never over HTTP", () => {
    expect(securityHeaders("n", https, false).response["Strict-Transport-Security"]).toBe("max-age=31536000; includeSubDomains");
    expect(securityHeaders("n", http, false).response["Strict-Transport-Security"]).toBeUndefined();
  });
});
