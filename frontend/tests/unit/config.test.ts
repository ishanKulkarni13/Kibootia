import { describe, expect, it } from "vitest";

import { loadConfig, MissingSettingError } from "@/lib/config";

const valid = { SESSION_SECRET: "x".repeat(40), KEYCLOAK_WEB_CLIENT_SECRET: "client-secret" };

const production = {
  ...valid,
  NODE_ENV: "production",
  PUBLIC_BASE_URL: "https://kibootia.example",
  KEYCLOAK_ISSUER: "https://auth.kibootia.example/realms/kibootia",
  API_BASE_URL: "http://backend:8080",
};

describe("frontend settings", () => {
  it("uses the local defaults when only the secrets are set", () => {
    const config = loadConfig(valid);
    expect(config).toMatchObject({
      publicBaseUrl: "http://localhost:3470",
      issuer: "http://localhost:8180/realms/kibootia",
      internalIssuer: "http://localhost:8180/realms/kibootia",
      apiBaseUrl: "http://localhost:8080",
      clientId: "kibootia-web",
    });
  });

  it("keeps the public and internal Keycloak addresses apart", () => {
    const config = loadConfig({ ...valid, KEYCLOAK_INTERNAL_ISSUER: "http://keycloak:8080/realms/kibootia/" });
    expect(config.issuer).toBe("http://localhost:8180/realms/kibootia");
    expect(config.internalIssuer).toBe("http://keycloak:8080/realms/kibootia");
  });

  it("names the missing setting without printing any value", () => {
    expect(() => loadConfig({ KEYCLOAK_WEB_CLIENT_SECRET: "secret-value" })).toThrow(/SESSION_SECRET/);
    try {
      loadConfig({ SESSION_SECRET: "x".repeat(40) });
      expect.unreachable();
    } catch (error) {
      expect(error).toBeInstanceOf(MissingSettingError);
      expect((error as Error).message).toContain("KEYCLOAK_WEB_CLIENT_SECRET");
      expect((error as Error).message).not.toContain("xxxx");
    }
  });

  it("refuses a session secret that is too short to be safe", () => {
    expect(() => loadConfig({ ...valid, SESSION_SECRET: "short" })).toThrow(/at least 32/);
  });
});

describe("frontend settings in production (ADR 0063)", () => {
  it("starts when every address and secret is set", () => {
    const config = loadConfig(production);
    expect(config.publicBaseUrl).toBe("https://kibootia.example");
    expect(config.issuer).toBe("https://auth.kibootia.example/realms/kibootia");
    expect(config.apiBaseUrl).toBe("http://backend:8080");
  });

  it.each(["PUBLIC_BASE_URL", "KEYCLOAK_ISSUER", "API_BASE_URL", "SESSION_SECRET", "KEYCLOAK_WEB_CLIENT_SECRET"])(
    "refuses to start without %s, and does not fall back to localhost",
    (name) => {
      const env: Record<string, string | undefined> = { ...production };
      delete env[name];
      expect(() => loadConfig(env)).toThrow(new RegExp(name));
      expect(() => loadConfig(env)).toThrow(/production/);
    },
  );

  it("treats an empty value as missing", () => {
    expect(() => loadConfig({ ...production, PUBLIC_BASE_URL: "" })).toThrow(/PUBLIC_BASE_URL/);
  });

  it("still falls back to the local defaults outside production", () => {
    expect(() => loadConfig({ ...valid, NODE_ENV: "development" })).not.toThrow();
    expect(() => loadConfig({ ...valid, NODE_ENV: "test" })).not.toThrow();
  });
});

describe("session keys (ADR 0063)", () => {
  const first = "a".repeat(40);
  const second = "b".repeat(40);

  it("takes one key as a list of one", () => {
    expect(loadConfig({ ...valid, SESSION_SECRET: first }).sessionSecret).toEqual([first]);
  });

  it("takes several keys, newest first, and ignores spaces and empty items", () => {
    expect(loadConfig({ ...valid, SESSION_SECRET: ` ${first} , ${second} ,` }).sessionSecret).toEqual([first, second]);
  });

  it("refuses a list in which any key is too short, and names the setting but not the key", () => {
    try {
      loadConfig({ ...valid, SESSION_SECRET: `${first},short-key` });
      expect.unreachable();
    } catch (error) {
      expect((error as Error).message).toMatch(/SESSION_SECRET.*at least 32/);
      expect((error as Error).message).not.toContain("short-key");
    }
  });

  it("refuses a list with no key in it", () => {
    expect(() => loadConfig({ ...valid, SESSION_SECRET: " , " })).toThrow(/SESSION_SECRET/);
  });
});

describe("proxy limits and the content security policy (ADR 0063)", () => {
  it("has defaults: 30 seconds, 120 for an upload, 6 MiB, no trusted proxy, report-only", () => {
    expect(loadConfig(valid)).toMatchObject({
      proxyTimeoutMs: 30_000,
      proxyUploadTimeoutMs: 120_000,
      proxyMaxBodyBytes: 6 * 1024 * 1024,
      trustedProxyHops: 0,
      cspMode: "report-only",
    });
  });

  it("reads the limits from the environment", () => {
    const config = loadConfig({
      ...valid,
      API_PROXY_TIMEOUT_MS: "5000",
      API_PROXY_UPLOAD_TIMEOUT_MS: "60000",
      API_PROXY_MAX_BODY_BYTES: "1048576",
      TRUSTED_PROXY_HOPS: "2",
      CSP_MODE: "enforce",
    });
    expect(config).toMatchObject({
      proxyTimeoutMs: 5000,
      proxyUploadTimeoutMs: 60_000,
      proxyMaxBodyBytes: 1_048_576,
      trustedProxyHops: 2,
      cspMode: "enforce",
    });
  });

  it.each([
    ["API_PROXY_TIMEOUT_MS", "soon"],
    ["API_PROXY_TIMEOUT_MS", "0"],
    ["API_PROXY_MAX_BODY_BYTES", "-5"],
    ["TRUSTED_PROXY_HOPS", "1.5"],
    ["TRUSTED_PROXY_HOPS", "-1"],
    ["CSP_MODE", "sometimes"],
  ])("refuses %s=%s", (name, value) => {
    expect(() => loadConfig({ ...valid, [name]: value })).toThrow(new RegExp(name));
  });
});
