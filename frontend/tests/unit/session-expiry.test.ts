// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, messageFor } from "@/lib/api/problem";
import { isSessionEnded, resetSendToSignIn, sendToSignIn, signInAddress } from "@/lib/session-expiry";

const problem = (status: number, code: string) => ({ type: "about:blank", title: "x", status, code });

describe("an ended session sends the user to sign in (finding S13)", () => {
  afterEach(() => resetSendToSignIn());

  it("recognises the BFF's and Spring's 401, and nothing else", () => {
    expect(isSessionEnded(new ApiError(401, problem(401, "bff.unauthenticated")))).toBe(true);
    expect(isSessionEnded(new ApiError(401, problem(401, "platform.unauthenticated")))).toBe(true);
    expect(isSessionEnded(new ApiError(403, problem(403, "platform.forbidden")))).toBe(false);
    expect(isSessionEnded(new ApiError(404))).toBe(false);
    expect(isSessionEnded(new Error("network"))).toBe(false);
  });

  it("comes back to the page and its query after signing in", () => {
    expect(signInAddress({ pathname: "/notes", search: "?q=plan" })).toBe("/auth/login?returnTo=%2Fnotes%3Fq%3Dplan");
  });

  it("navigates once, however many requests fail at the same moment", () => {
    const navigate = vi.fn();
    sendToSignIn(navigate);
    sendToSignIn(navigate);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(expect.stringMatching(/^\/auth\/login\?returnTo=/));
  });

  it("says why, for the moment before the browser leaves", () => {
    expect(messageFor(new ApiError(401, problem(401, "bff.unauthenticated")))).toBe("Your session has ended. Sign in again.");
  });
});
