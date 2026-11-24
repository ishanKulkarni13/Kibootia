"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type AuditEntry = components["schemas"]["AuditEntryResponse"];
export type Person = components["schemas"]["PersonResponse"];

export interface AuditParams {
  page: number;
  size: number;
  action?: string;
  targetType?: string;
}

export function useAuditEntries(params: AuditParams) {
  return useQuery({
    queryKey: ["audit", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/audit-entries", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

export type FailedEvent = components["schemas"]["FailedEventResponse"];

/** Events whose delivery failed, oldest first (ADR 0031). The list never holds an event's payload. */
export function useFailedEvents(params: { page: number; size: number }) {
  return useQuery({
    queryKey: ["failed-events", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/failed-events", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

/** Delivers one failed event again now. The retry is audited, so the audit log is refreshed with the list. */
export function useRetryFailedEvent() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      unwrap(await browserApi().POST("/api/v1/failed-events/{id}/retry", { params: { path: { id } } }));
    },
    onSuccess: () =>
      Promise.all([client.invalidateQueries({ queryKey: ["failed-events"] }), client.invalidateQueries({ queryKey: ["audit"] })]),
  });
}
