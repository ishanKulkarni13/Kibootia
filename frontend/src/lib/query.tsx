"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

import { ApiError } from "@/lib/api/problem";
import { isSessionEnded, sendToSignIn } from "@/lib/session-expiry";

/** A query or a save that failed because the session ended sends the user to sign in again, from wherever it failed. */
function onError(error: unknown) {
  if (isSessionEnded(error)) sendToSignIn();
}

/**
 * One query client per browser tab (ADR 0038). Reads are kept fresh for a short time, and a request the API refused for
 * a reason retrying cannot fix (401, 403, 404, 409) is not repeated.
 */
export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        queryCache: new QueryCache({ onError }),
        mutationCache: new MutationCache({ onError }),
        defaultOptions: {
          queries: {
            staleTime: 15_000,
            refetchOnWindowFocus: false,
            retry: (failures, error) => {
              if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
              return failures < 2;
            },
          },
        },
      }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
