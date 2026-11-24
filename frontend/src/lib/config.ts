import "server-only";

/**
 * Server-side settings of the Next.js server (ADR 0026, ADR 0040, ADR 0063). Read when first used, so a missing required
 * value fails the first request with a clear message instead of failing in the middle of a sign-in.
 *
 * Keycloak has two addresses: the public one that browsers use and that appears as the token issuer, and an
 * internal one for the server's own calls (the same host when both run on the developer's machine, `keycloak:8080`
 * inside the `apps` Compose profile).
 *
 * Outside production the addresses fall back to the developer's machine. In production (`NODE_ENV=production`) none
 * does: a server that started without `PUBLIC_BASE_URL`, `KEYCLOAK_ISSUER` or `API_BASE_URL` would run quietly against
 * localhost over HTTP, so it refuses to start instead.
 */
export interface Config {
  /** Public origin of this server, without a trailing slash. */
  publicBaseUrl: string;
  /** Public realm URL; the exact `iss` value of tokens. Browsers are sent here. */
  issuer: string;
  /** Realm URL the server itself uses for token and key requests. */
  internalIssuer: string;
  clientId: string;
  clientSecret: string;
  /**
   * The keys that seal and open the session cookie, newest first (ADR 0063): the first seals, every one opens. To rotate,
   * put the new key in front and leave the old one behind it until the sessions it sealed have ended.
   */
  sessionSecret: string[];
  /** Fixed upstream of the `/api` proxy: the Spring API. */
  apiBaseUrl: string;
  /** How long the `/api` proxy waits for the API, in milliseconds. */
  proxyTimeoutMs: number;
  /** The same for a request that uploads a file (`multipart/form-data`). */
  proxyUploadTimeoutMs: number;
  /** The largest request body the `/api` proxy passes on, in bytes. It is the API's own upload limit plus the envelope. */
  proxyMaxBodyBytes: number;
  /**
   * How many proxies stand between the browser and this server and are trusted to append the client's address to
   * `X-Forwarded-For`. 0 trusts none: the client address is then not passed on, because a caller could invent it.
   */
  trustedProxyHops: number;
  /** `report-only` (the default), `enforce` or `off`: what the Content-Security-Policy does. */
  cspMode: "report-only" | "enforce" | "off";
}

export class MissingSettingError extends Error {
  constructor(readonly setting: string, hint: string) {
    super(`Missing required setting ${setting}. ${hint}`);
    this.name = "MissingSettingError";
  }
}

type Env = Record<string, string | undefined>;

const SECRET_MIN_LENGTH = 32;

function required(env: Env, name: string, hint: string): string {
  const value = env[name];
  if (!value) throw new MissingSettingError(name, hint);
  return value;
}

function stripTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

/** A whole number setting with a default; a value that is not a whole number at or above the minimum is an error. */
function whole(env: Env, name: string, otherwise: number, minimum: number): number {
  const raw = env[name];
  if (raw === undefined || raw.trim() === "") return otherwise;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < minimum) {
    throw new MissingSettingError(name, `It must be a whole number of at least ${minimum}.`);
  }
  return value;
}

/** The session keys: one, or several separated by commas with the newest first. */
function sessionKeys(env: Env, hint: string): string[] {
  const keys = required(env, "SESSION_SECRET", hint)
    .split(",")
    .map((key) => key.trim())
    .filter((key) => key !== "");
  if (keys.length === 0) throw new MissingSettingError("SESSION_SECRET", hint);
  for (const key of keys) {
    if (key.length < SECRET_MIN_LENGTH) {
      throw new MissingSettingError("SESSION_SECRET", `Each key must be at least ${SECRET_MIN_LENGTH} characters; 'pnpm bootstrap' generates one.`);
    }
  }
  return keys;
}

function cspMode(env: Env): Config["cspMode"] {
  const mode = (env.CSP_MODE || "report-only").trim();
  if (mode !== "report-only" && mode !== "enforce" && mode !== "off") {
    throw new MissingSettingError("CSP_MODE", "It must be report-only, enforce or off.");
  }
  return mode;
}

export function loadConfig(env: Env = process.env): Config {
  const production = env.NODE_ENV === "production";
  const hint = production
    ? "Set it in the environment of the server; in production nothing falls back to a default."
    : "Run 'pnpm bootstrap' to create .env, then start the server with 'pnpm dev'.";
  // A setting with a development default is required outright in production.
  const address = (name: string, development: string): string => stripTrailingSlash(production ? required(env, name, hint) : env[name] || development);

  const issuer = address("KEYCLOAK_ISSUER", "http://localhost:8180/realms/kibootia");
  return {
    publicBaseUrl: address("PUBLIC_BASE_URL", "http://localhost:3470"),
    issuer,
    internalIssuer: stripTrailingSlash(env.KEYCLOAK_INTERNAL_ISSUER || issuer),
    clientId: env.KEYCLOAK_WEB_CLIENT_ID || "kibootia-web",
    clientSecret: required(env, "KEYCLOAK_WEB_CLIENT_SECRET", hint),
    sessionSecret: sessionKeys(env, hint),
    apiBaseUrl: address("API_BASE_URL", "http://localhost:8080"),
    proxyTimeoutMs: whole(env, "API_PROXY_TIMEOUT_MS", 30_000, 1),
    proxyUploadTimeoutMs: whole(env, "API_PROXY_UPLOAD_TIMEOUT_MS", 120_000, 1),
    proxyMaxBodyBytes: whole(env, "API_PROXY_MAX_BODY_BYTES", 6 * 1024 * 1024, 1),
    trustedProxyHops: whole(env, "TRUSTED_PROXY_HOPS", 0, 0),
    cspMode: cspMode(env),
  };
}

let cached: Config | undefined;

export function config(): Config {
  cached ??= loadConfig();
  return cached;
}
