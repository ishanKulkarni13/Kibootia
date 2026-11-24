import { context, propagation, type Context } from "@opentelemetry/api";

import { checkCsrf } from "../csrf";
import { logger } from "../logger";
import { problem } from "../problem";
import type { Deps } from "./deps";
import { currentSession } from "./refresh";

/**
 * The generic `/api/*` proxy (ADR 0042). The upstream is fixed to the Spring API, so the browser can never use this
 * server to reach another host. It attaches the access token, enforces the CSRF rule, streams bodies in both
 * directions and passes W3C trace context. The browser never sees a token, and cookies never reach Spring.
 *
 * Hardening (ADR 0063): only the request headers the API needs are forwarded, a request that waits too long for the API
 * is answered 504, a request body larger than the API accepts is refused before it is streamed, and the client's
 * address is passed on only from the proxies this deployment says it trusts.
 */

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "proxy-connection",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

/**
 * The request headers that are forwarded: an allow-list, so a header the API does not know about, or one a client made
 * up to confuse it, never arrives. Credentials, the browser's origin data and the CSRF header are not on it, and neither
 * are trace headers: those come from this server's own span, never from the browser (a caller could forge trace links).
 * The body's length is left out because the body is streamed and the upstream request frames it again.
 */
const FORWARDED_REQUEST_HEADERS = new Set([
  "accept",
  "accept-encoding",
  "accept-language",
  "content-type",
  "if-match",
  "if-modified-since",
  "if-none-match",
  "user-agent",
]);

/** The body is already decoded by `fetch`, so the upstream's encoding and length no longer describe it. */
const DROPPED_RESPONSE_HEADERS = new Set([...HOP_BY_HOP, "set-cookie", "content-encoding", "content-length"]);

const ALLOWED_METHODS = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"]);
const NO_BODY_STATUSES = new Set([101, 204, 205, 304]);

/** Rejects anything that could climb out of /api: dot segments, encoded slashes and control characters. */
export function isSafeApiPath(pathname: string): boolean {
  if (!pathname.startsWith("/api/") || pathname.length === "/api/".length) return false;
  for (const raw of pathname.split("/").slice(1)) {
    let segment: string;
    try {
      segment = decodeURIComponent(raw);
    } catch {
      return false;
    }
    if (segment === "." || segment === ".." || segment.includes("/") || segment.includes("\\") || /[\u0000-\u001f]/.test(segment)) {
      return false;
    }
  }
  return true;
}

/**
 * The client's address, from `X-Forwarded-For`, or null when it cannot be trusted. Each trusted proxy appends the
 * address it saw to the right of the list, so with N trusted proxies the client's address is the Nth from the right;
 * anything to its left was written by the client and means nothing. With no trusted proxy there is nothing to read, and
 * the API sees this server's address.
 */
export function clientAddress(forwardedFor: string | null, trustedHops: number): string | null {
  if (trustedHops < 1 || !forwardedFor) return null;
  const addresses = forwardedFor.split(",").map((address) => address.trim()).filter((address) => address !== "");
  const address = addresses[addresses.length - trustedHops];
  // Only the characters of an IPv4 or IPv6 address: the value goes into a header the API reads.
  return address !== undefined && /^[0-9a-fA-F:.]{2,45}$/.test(address) ? address : null;
}

/** Counts the bytes of a streamed request body and fails the stream when it passes the limit. */
class BodyLimit {
  exceeded = false;

  constructor(private readonly max: number) {}

  wrap(body: ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
    let seen = 0;
    return body.pipeThrough(
      new TransformStream<Uint8Array, Uint8Array>({
        transform: (chunk, controller) => {
          seen += chunk.byteLength;
          if (seen > this.max) {
            this.exceeded = true;
            controller.error(new Error("request body too large"));
            return;
          }
          controller.enqueue(chunk);
        },
      }),
    );
  }
}

export async function proxyApi(request: Request, deps: Deps, traceContext: Context = context.active()): Promise<Response> {
  const { config } = deps;
  if (!ALLOWED_METHODS.has(request.method.toUpperCase())) {
    return problem(405, "bff.method_not_allowed", "The method is not allowed", { Allow: [...ALLOWED_METHODS].join(", ") });
  }

  const rejected = checkCsrf(request, config.publicBaseUrl);
  if (rejected) return rejected;

  const incoming = new URL(request.url);
  if (!isSafeApiPath(incoming.pathname)) {
    return problem(400, "bff.invalid_path", "The request path is not valid");
  }

  // A body that declares itself too large is refused before anything is read or streamed.
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > config.proxyMaxBodyBytes) {
    return problem(413, "bff.body_too_large", "The request is too large");
  }

  const result = await currentSession(request.headers.get("cookie"), deps);
  if (result.status !== "ok") {
    const headers = new Headers();
    for (const cookie of result.setCookies) headers.append("Set-Cookie", cookie);
    return problem(401, "bff.unauthenticated", "Sign in to continue", headers);
  }

  const target = new URL(`${config.apiBaseUrl}${incoming.pathname}${incoming.search}`);
  if (target.origin !== new URL(config.apiBaseUrl).origin) {
    return problem(400, "bff.invalid_path", "The request path is not valid");
  }

  const headers = new Headers();
  for (const [name, value] of request.headers) {
    if (FORWARDED_REQUEST_HEADERS.has(name.toLowerCase())) headers.set(name, value);
  }
  headers.set("Authorization", `Bearer ${result.session.accessToken}`);
  const client = clientAddress(request.headers.get("x-forwarded-for"), config.trustedProxyHops);
  if (client) headers.set("X-Forwarded-For", client);
  propagation.inject(traceContext, headers, {
    set: (carrier, key, value) => {
      carrier.set(key, value);
    },
  });

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const limit = new BodyLimit(config.proxyMaxBodyBytes);
  const uploading = (request.headers.get("content-type") ?? "").toLowerCase().startsWith("multipart/form-data");
  const timeout = AbortSignal.timeout(uploading ? config.proxyUploadTimeoutMs : config.proxyTimeoutMs);
  let upstream: Response;
  try {
    upstream = await deps.fetch(target, {
      method: request.method,
      headers,
      body: hasBody && request.body ? limit.wrap(request.body) : undefined,
      // Streams the request body instead of buffering it.
      duplex: "half",
      redirect: "manual",
      signal: AbortSignal.any([request.signal, timeout]),
    } as RequestInit & { duplex: "half" });
  } catch (error) {
    if (limit.exceeded) {
      return problem(413, "bff.body_too_large", "The request is too large", cookieHeaders(result.setCookies));
    }
    if (timeout.aborted) {
      logger.warn({ path: incoming.pathname }, "API did not answer in time");
      return problem(504, "bff.upstream_timeout", "The API did not answer in time", cookieHeaders(result.setCookies));
    }
    logger.error({ reason: error instanceof Error ? error.name : "unknown", path: incoming.pathname }, "API upstream unreachable");
    return problem(502, "bff.upstream_unavailable", "The API is not reachable", cookieHeaders(result.setCookies));
  }

  const responseHeaders = new Headers();
  for (const [name, value] of upstream.headers) {
    if (!DROPPED_RESPONSE_HEADERS.has(name.toLowerCase())) responseHeaders.set(name, value);
  }
  // Only the refreshed session may set cookies; whatever the upstream asks for is ignored.
  for (const cookie of result.setCookies) responseHeaders.append("Set-Cookie", cookie);

  logger.debug({ method: request.method, path: incoming.pathname, status: upstream.status }, "proxied API request");
  return new Response(NO_BODY_STATUSES.has(upstream.status) ? null : upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

function cookieHeaders(setCookies: string[]): Headers {
  const headers = new Headers();
  for (const cookie of setCookies) headers.append("Set-Cookie", cookie);
  return headers;
}
