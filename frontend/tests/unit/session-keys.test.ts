import { createSecretKey, hkdfSync } from "node:crypto";

import { decodeProtectedHeader, EncryptJWT } from "jose";
import { describe, expect, it } from "vitest";

import { cookieNames, newSession, readSession, seal, unseal, writeSession } from "@/lib/session";

import { cookieHeaderFrom } from "./helpers";

const NEW_KEY = "n".repeat(40);
const OLD_KEY = "o".repeat(40);
const OTHER_KEY = "x".repeat(40);

const tokens = { accessToken: "a", refreshToken: "r", idToken: "i", accessExpiresAt: Math.floor(Date.now() / 1000) + 300 };

describe("the session key ring (ADR 0063)", () => {
  it("seals with the first key and says which one in the header, without revealing it", async () => {
    const sealed = await seal({ hello: "world" }, [NEW_KEY, OLD_KEY], 60);
    const { kid } = decodeProtectedHeader(sealed);
    expect(kid).toMatch(/^[0-9a-f]{8}$/);
    expect(NEW_KEY).not.toContain(kid as string);
    expect(await unseal(sealed, [NEW_KEY])).toMatchObject({ hello: "world" });
    expect(await unseal(sealed, [OLD_KEY])).toBeNull();
  });

  it("opens what an older key sealed, so a rotation does not sign everyone out", async () => {
    const sealedByOld = await seal({ hello: "old" }, OLD_KEY, 60);
    expect(await unseal(sealedByOld, [NEW_KEY, OLD_KEY])).toMatchObject({ hello: "old" });
  });

  it("stops opening what a key sealed once that key has been dropped from the list", async () => {
    const sealedByOld = await seal({ hello: "old" }, OLD_KEY, 60);
    expect(await unseal(sealedByOld, [NEW_KEY])).toBeNull();
  });

  it("refuses a token sealed with a key that is not in the list", async () => {
    const sealed = await seal({ hello: "other" }, OTHER_KEY, 60);
    expect(await unseal(sealed, [NEW_KEY, OLD_KEY])).toBeNull();
  });

  it("still opens a token written before keys had identifiers, which is how every existing session was sealed", async () => {
    // Sealed exactly as the code did before the key ring: the same key derivation, and no kid in the header.
    const legacyKey = createSecretKey(Buffer.from(hkdfSync("sha256", OLD_KEY, "", "kibootia-session-cookie", 32)));
    const legacy = await new EncryptJWT({ hello: "legacy" })
      .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
      .setIssuedAt()
      .setExpirationTime("60s")
      .encrypt(legacyKey);
    expect(decodeProtectedHeader(legacy).kid).toBeUndefined();

    expect(await unseal(legacy, [NEW_KEY, OLD_KEY])).toMatchObject({ hello: "legacy" });
    expect(await unseal(legacy, [NEW_KEY])).toBeNull();
  });

  it("takes a single key as a plain string", async () => {
    const sealed = await seal({ hello: "string" }, NEW_KEY, 60);
    expect(await unseal(sealed, NEW_KEY)).toMatchObject({ hello: "string" });
  });

  it("returns null for something that is not a token", async () => {
    expect(await unseal("not-a-token", [NEW_KEY])).toBeNull();
    expect(await unseal("", [NEW_KEY])).toBeNull();
  });

  it("reads a session cookie after the keys were rotated", async () => {
    const header = cookieHeaderFrom(await writeSession(newSession(tokens), OLD_KEY, false));
    expect(await readSession(header, [NEW_KEY, OLD_KEY])).toMatchObject({ accessToken: "a" });
    expect(await readSession(header, [NEW_KEY])).toBeNull();
  });
});

describe("cookie names (ADR 0063)", () => {
  it("carries the __Host- prefix over HTTPS and none over HTTP", () => {
    expect(cookieNames(true)).toEqual({ session: "__Host-kibootia_session", login: "__Host-kibootia_login" });
    expect(cookieNames(false)).toEqual({ session: "kibootia_session", login: "kibootia_login" });
  });

  it("writes session cookies the browser accepts with the prefix: Secure, Path=/, no Domain", async () => {
    const cookies = await writeSession(newSession(tokens), NEW_KEY, true);
    expect(cookies.length).toBeGreaterThan(0);
    for (const cookie of cookies) {
      expect(cookie).toMatch(/^__Host-kibootia_session\.\d+=/);
      expect(cookie).toContain("Secure");
      expect(cookie).toContain("Path=/");
      expect(cookie).toContain("HttpOnly");
      expect(cookie).not.toMatch(/Domain=/i);
    }
  });

  it("reads only the cookie of its own mode, so a plain cookie cannot stand in for a prefixed one", async () => {
    const plain = cookieHeaderFrom(await writeSession(newSession(tokens), NEW_KEY, false));
    expect(await readSession(plain, [NEW_KEY], false)).toMatchObject({ accessToken: "a" });
    expect(await readSession(plain, [NEW_KEY], true)).toBeNull();

    const prefixed = cookieHeaderFrom(await writeSession(newSession(tokens), NEW_KEY, true));
    expect(await readSession(prefixed, [NEW_KEY], true)).toMatchObject({ accessToken: "a" });
    expect(await readSession(prefixed, [NEW_KEY], false)).toBeNull();
  });
});
