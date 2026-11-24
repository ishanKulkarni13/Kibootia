import { ApiError } from "@/lib/api/problem";

/**
 * Whether a failed call means the session has ended: the BFF has no session to attach (`bff.unauthenticated`), or Spring
 * refused the token (`platform.unauthenticated`). Either way, signing in again is the only thing that helps, so the user
 * is sent there instead of being shown "The request failed" (finding S13 of the security review).
 */
export function isSessionEnded(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

/** The sign-in address that brings the user back to where they are. Only a same-site path is passed (the BFF checks it too). */
export function signInAddress(location: Pick<Location, "pathname" | "search">): string {
  return `/auth/login?returnTo=${encodeURIComponent(`${location.pathname}${location.search}`)}`;
}

let sending = false;

/** Sends the browser to sign in, once, however many requests fail with the session at the same moment. */
export function sendToSignIn(navigate: (address: string) => void = (address) => window.location.assign(address)) {
  if (sending) return;
  sending = true;
  navigate(signInAddress(window.location));
}

/** For tests: forget that the browser is already on its way to sign in. */
export function resetSendToSignIn() {
  sending = false;
}
