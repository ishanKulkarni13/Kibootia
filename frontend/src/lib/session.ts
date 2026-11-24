import { createHash, createSecretKey, hkdfSync } from "node:crypto";

import { decodeProtectedHeader, EncryptJWT, jwtDecrypt } from "jose";

import { expireCookie, parseCookies, serializeCookie } from "./cookies";

/**
 * The signed-in user's session: tokens held on the server side of the browser boundary, in an encrypted HttpOnly
 * cookie that is split into chunks when it is too large for one cookie (ADR 0026). The browser never sees a token.
 */
export interface Session {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  /** Epoch seconds at which the access token expires. */
  accessExpiresAt: number;
  /** Epoch seconds after which the session ends, however often tokens are refreshed. */
  sessionExpiresAt: number;
}

/** The base names; over HTTPS the cookies carry the `__Host-` prefix (see {@link cookieNames}). */
export const SESSION_COOKIE = "kibootia_session";
export const LOGIN_COOKIE = "kibootia_login";

/**
 * The cookie names for a deployment. Over HTTPS the `__Host-` prefix makes the browser refuse a cookie of that name
 * unless it is Secure, set from this host, with no Domain and with Path=/, so a sibling subdomain cannot plant one
 * (ADR 0063). Over plain HTTP, which only local development uses, the prefix cannot be used because it requires Secure.
 */
export function cookieNames(secure: boolean): { session: string; login: string } {
  const prefix = secure ? "__Host-" : "";
  return { session: `${prefix}${SESSION_COOKIE}`, login: `${prefix}${LOGIN_COOKIE}` };
}

/** A key, or the keys of a rotation, newest first. */
export type SessionKeys = string | readonly string[];

/** Below the 4096-byte cookie limit once the name and attributes are added. */
export const CHUNK_SIZE = 3600;
const MAX_CHUNKS = 8;
const SESSION_MAX_AGE_SECONDS = 10 * 60 * 60;

function keysOf(secrets: SessionKeys): readonly string[] {
  return typeof secrets === "string" ? [secrets] : secrets;
}

function key(secret: string) {
  // HKDF keeps the cookie key independent of the raw secret and gives the 32 bytes A256GCM needs.
  return createSecretKey(Buffer.from(hkdfSync("sha256", secret, "", "kibootia-session-cookie", 32)));
}

/** A short identifier of a key, written in the token's header so the right key is tried first. It does not reveal the key. */
function keyId(secret: string): string {
  return createHash("sha256").update("kibootia-session-key-id:").update(secret).digest("hex").slice(0, 8);
}

/** Seals with the first (newest) key. */
export async function seal(payload: Record<string, unknown>, secrets: SessionKeys, ttlSeconds: number): Promise<string> {
  const [newest] = keysOf(secrets);
  if (newest === undefined) throw new Error("There is no session key to seal with");
  return new EncryptJWT(payload)
    .setProtectedHeader({ alg: "dir", enc: "A256GCM", kid: keyId(newest) })
    .setIssuedAt()
    .setExpirationTime(`${ttlSeconds}s`)
    .encrypt(key(newest));
}

/**
 * Opens with any of the keys: the one the header names first, then the others, so a token that was sealed before keys
 * had identifiers, or with a key that has since been dropped, is still tried honestly and refused when none fits.
 */
export async function unseal<T>(token: string, secrets: SessionKeys): Promise<T | null> {
  let named: string | undefined;
  try {
    named = decodeProtectedHeader(token).kid;
  } catch {
    return null;
  }
  const candidates = [...keysOf(secrets)].sort((a, b) => Number(keyId(b) === named) - Number(keyId(a) === named));
  for (const candidate of candidates) {
    try {
      const { payload } = await jwtDecrypt(token, key(candidate), { keyManagementAlgorithms: ["dir"], contentEncryptionAlgorithms: ["A256GCM"] });
      return payload as T;
    } catch {
      // Tampered, expired or sealed with another key: try the next key, and treat all of these as "no session" at the end.
    }
  }
  return null;
}

export function newSession(tokens: Pick<Session, "accessToken" | "refreshToken" | "idToken" | "accessExpiresAt">, now = Date.now()): Session {
  return { ...tokens, sessionExpiresAt: Math.floor(now / 1000) + SESSION_MAX_AGE_SECONDS };
}

export async function readSession(cookieHeader: string | null | undefined, secrets: SessionKeys, secure = false): Promise<Session | null> {
  const cookies = parseCookies(cookieHeader);
  const name = cookieNames(secure).session;
  let sealed = "";
  for (let index = 0; index < MAX_CHUNKS; index++) {
    const chunk = cookies.get(`${name}.${index}`);
    if (chunk === undefined) break;
    sealed += chunk;
  }
  if (!sealed) return null;
  const session = await unseal<Session>(sealed, secrets);
  if (!session?.accessToken || !session.refreshToken) return null;
  if (session.sessionExpiresAt * 1000 <= Date.now()) return null;
  return session;
}

/** Set-Cookie values that store the session and remove chunks left over from a previously larger one. */
export async function writeSession(session: Session, secrets: SessionKeys, secure: boolean, existingCookieHeader?: string | null): Promise<string[]> {
  const name = cookieNames(secure).session;
  const ttl = Math.max(1, session.sessionExpiresAt - Math.floor(Date.now() / 1000));
  const sealed = await seal({ ...session }, secrets, ttl);
  const chunks: string[] = [];
  for (let offset = 0; offset < sealed.length; offset += CHUNK_SIZE) chunks.push(sealed.slice(offset, offset + CHUNK_SIZE));
  if (chunks.length > MAX_CHUNKS) throw new Error("The session is too large to store in cookies");
  const cookies = chunks.map((chunk, index) => serializeCookie(`${name}.${index}`, chunk, { maxAge: ttl, secure }));
  for (const existing of parseCookies(existingCookieHeader).keys()) {
    if (existing.startsWith(`${name}.`) && Number(existing.slice(name.length + 1)) >= chunks.length) {
      cookies.push(expireCookie(existing, secure));
    }
  }
  return cookies;
}

export function clearSessionCookies(existingCookieHeader: string | null | undefined, secure: boolean): string[] {
  const name = cookieNames(secure).session;
  const names = [...parseCookies(existingCookieHeader).keys()].filter((existing) => existing.startsWith(`${name}.`));
  return names.map((existing) => expireCookie(existing, secure));
}
