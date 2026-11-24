import { describe, expect, it, vi } from "vitest";

import { clientAddress, proxyApi } from "@/lib/bff/proxy";

import { config, makeDeps, request, sessionCookie } from "./helpers";

/** Deps whose upstream records what it was sent and answers 200. */
function recordingDeps(overrides: Partial<typeof config> = {}) {
  const fetch = vi.fn(async () => new Response("{}", { status: 200 }));
  const deps = makeDeps({ fetch: fetch as unknown as typeof globalThis.fetch, config: { ...config, ...overrides } });
  return { deps, fetch };
}

function sentHeaders(fetch: ReturnType<typeof vi.fn>): Headers {
  const [, init] = fetch.mock.calls[0] as [URL, RequestInit];
  return new Headers(init.headers);
}

describe("/api proxy: only what the API needs is forwarded (ADR 0063)", () => {
  it("forwards the headers on the allow-list", async () => {
    const { deps, fetch } = recordingDeps();
    await proxyApi(
      request("/api/v1/me", {
        cookie: await sessionCookie(),
        headers: {
          accept: "application/json",
          "accept-language": "en",
          "content-type": "application/json",
          "if-none-match": '"v1"',
          "if-match": '"v1"',
          "user-agent": "a-browser",
        },
      }),
      deps,
    );
    const headers = sentHeaders(fetch);
    expect(headers.get("accept")).toBe("application/json");
    expect(headers.get("accept-language")).toBe("en");
    expect(headers.get("content-type")).toBe("application/json");
    expect(headers.get("if-none-match")).toBe('"v1"');
    expect(headers.get("if-match")).toBe('"v1"');
    expect(headers.get("user-agent")).toBe("a-browser");
  });

  it("drops a header the API has no use for, whatever it is called", async () => {
    const { deps, fetch } = recordingDeps();
    await proxyApi(
      request("/api/v1/me", {
        cookie: await sessionCookie(),
        headers: { "x-anything": "1", "x-custom-flag": "on", "x-http-method-override": "DELETE", "x-original-url": "/admin", dnt: "1", pragma: "no-cache" },
      }),
      deps,
    );
    const names = [...sentHeaders(fetch).keys()];
    expect(names).not.toContain("x-anything");
    expect(names).not.toContain("x-custom-flag");
    expect(names).not.toContain("x-http-method-override");
    expect(names).not.toContain("x-original-url");
    expect(names).not.toContain("dnt");
    expect(names).not.toContain("pragma");
  });

  it("adds the bearer token and sends no cookie, origin or CSRF header", async () => {
    const { deps, fetch } = recordingDeps();
    await proxyApi(request("/api/v1/me", { cookie: await sessionCookie(), headers: { authorization: "Bearer from-the-browser" } }), deps);
    const headers = sentHeaders(fetch);
    expect(headers.get("authorization")).toBe("Bearer access-1");
    expect(headers.get("cookie")).toBeNull();
    expect(headers.get("origin")).toBeNull();
    expect(headers.get("x-kibootia-request")).toBeNull();
  });
});

describe("/api proxy: the client's address (ADR 0063)", () => {
  it("passes no address on when no proxy is trusted, because anyone can write X-Forwarded-For", async () => {
    const { deps, fetch } = recordingDeps({ trustedProxyHops: 0 });
    await proxyApi(request("/api/v1/me", { cookie: await sessionCookie(), headers: { "x-forwarded-for": "6.6.6.6" } }), deps);
    expect(sentHeaders(fetch).get("x-forwarded-for")).toBeNull();
  });

  it("passes on the address the trusted proxy saw, and nothing the client wrote to its left", async () => {
    const { deps, fetch } = recordingDeps({ trustedProxyHops: 1 });
    await proxyApi(request("/api/v1/me", { cookie: await sessionCookie(), headers: { "x-forwarded-for": "6.6.6.6, 203.0.113.9" } }), deps);
    expect(sentHeaders(fetch).get("x-forwarded-for")).toBe("203.0.113.9");
  });

  it("reads the right place for each number of trusted proxies", () => {
    expect(clientAddress("1.1.1.1, 2.2.2.2, 3.3.3.3", 1)).toBe("3.3.3.3");
    expect(clientAddress("1.1.1.1, 2.2.2.2, 3.3.3.3", 2)).toBe("2.2.2.2");
    expect(clientAddress("1.1.1.1, 2.2.2.2, 3.3.3.3", 3)).toBe("1.1.1.1");
  });

  it("passes on nothing when there are fewer addresses than trusted proxies, or none", () => {
    expect(clientAddress("1.1.1.1", 2)).toBeNull();
    expect(clientAddress(null, 1)).toBeNull();
    expect(clientAddress("", 1)).toBeNull();
    expect(clientAddress("1.1.1.1", 0)).toBeNull();
  });

  it("accepts IPv6 and refuses anything that is not an address", () => {
    expect(clientAddress("2001:db8::1", 1)).toBe("2001:db8::1");
    expect(clientAddress("unknown", 1)).toBeNull();
    expect(clientAddress("1.1.1.1\r\nX-Evil: 1", 1)).toBeNull();
    expect(clientAddress("<script>", 1)).toBeNull();
  });
});

describe("/api proxy: time limit (ADR 0063)", () => {
  /** An upstream that never answers until the call is aborted. */
  function hangingFetch() {
    return vi.fn((_url: URL, init: RequestInit) => {
      return new Promise<Response>((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(init.signal?.reason ?? new Error("aborted")));
      });
    });
  }

  it("answers 504 when the API does not answer in time", async () => {
    const fetch = hangingFetch();
    const deps = makeDeps({ fetch: fetch as unknown as typeof globalThis.fetch, config: { ...config, proxyTimeoutMs: 30 } });
    const response = await proxyApi(request("/api/v1/slow", { cookie: await sessionCookie() }), deps);
    expect(response.status).toBe(504);
    expect((await response.json()).code).toBe("bff.upstream_timeout");
  });

  it("gives an upload longer than an ordinary request", async () => {
    const fetch = hangingFetch();
    const deps = makeDeps({ fetch: fetch as unknown as typeof globalThis.fetch, config: { ...config, proxyTimeoutMs: 30, proxyUploadTimeoutMs: 60_000 } });
    const pending = proxyApi(
      request("/api/v1/me/avatar", {
        method: "POST",
        body: "x",
        csrf: true,
        cookie: await sessionCookie(),
        headers: { "content-type": "multipart/form-data; boundary=b" },
      }),
      deps,
    );
    const outcome = await Promise.race([pending.then(() => "answered"), new Promise((resolve) => setTimeout(() => resolve("still waiting"), 200))]);
    expect(outcome).toBe("still waiting");
  });

  it("still answers 502, not 504, when the API cannot be reached at all", async () => {
    const fetch = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    const deps = makeDeps({ fetch: fetch as unknown as typeof globalThis.fetch });
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), deps);
    expect(response.status).toBe(502);
  });
});

describe("/api proxy: size limit (ADR 0063)", () => {
  it("refuses a body that declares itself too large, before the API or the session is touched", async () => {
    const { deps, fetch } = recordingDeps({ proxyMaxBodyBytes: 100 });
    // A request that comes in over the network carries its length; one built in a test does not, so it is given.
    const response = await proxyApi(
      request("/api/v1/things", {
        method: "POST",
        body: "x".repeat(200),
        csrf: true,
        cookie: await sessionCookie(),
        headers: { "content-length": "200" },
      }),
      deps,
    );
    expect(response.status).toBe(413);
    expect((await response.json()).code).toBe("bff.body_too_large");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("lets a body at the limit through", async () => {
    const { deps, fetch } = recordingDeps({ proxyMaxBodyBytes: 100 });
    const response = await proxyApi(
      request("/api/v1/things", { method: "POST", body: "x".repeat(100), csrf: true, cookie: await sessionCookie() }),
      deps,
    );
    expect(response.status).toBe(200);
    expect(fetch).toHaveBeenCalledOnce();
  });

  it("stops a streamed body that has no declared length once it passes the limit", async () => {
    // The upstream reads the body the way the real one does, so the byte counter in front of it sees every chunk.
    const fetch = vi.fn(async (_url: URL, init: RequestInit) => {
      const reader = (init.body as ReadableStream<Uint8Array>).getReader();
      for (;;) {
        const { done } = await reader.read();
        if (done) break;
      }
      return new Response("{}", { status: 200 });
    });
    const deps = makeDeps({ fetch: fetch as unknown as typeof globalThis.fetch, config: { ...config, proxyMaxBodyBytes: 100 } });
    const chunk = new TextEncoder().encode("y".repeat(60));
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(chunk);
        controller.enqueue(chunk);
        controller.close();
      },
    });
    const incoming = new Request("http://localhost:3000/api/v1/things", {
      method: "POST",
      body,
      duplex: "half",
      headers: { origin: "http://localhost:3000", "x-kibootia-request": "1", cookie: await sessionCookie() },
    } as RequestInit);

    const response = await proxyApi(incoming, deps);

    expect(response.status).toBe(413);
    expect((await response.json()).code).toBe("bff.body_too_large");
  });
});
