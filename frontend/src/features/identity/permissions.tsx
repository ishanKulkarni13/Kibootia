"use client";

import { createContext, useContext, useMemo } from "react";

import type { Me } from "@/lib/api/client";

/**
 * What the signed-in user may do, as `GET /api/v1/me` reports it (ADR 0038). This is for usability only: it hides or
 * disables what would be refused anyway. The backend enforces every check, so nothing here is a security control.
 */
export interface Permissions {
  me: Me;
  /** Whether the user holds the action at all, for some records or for every one. */
  has(permission: string): boolean;
  hasAny(...permissions: string[]): boolean;
  /** How far the user's grants for the action reach (`all`, or a relation such as `relation:owner`); empty when not held. */
  reaches(permission: string): readonly string[];
  /** Whether the user holds the action for every record, not only their own (ADR 0054). */
  reachesAll(permission: string): boolean;
}

const PermissionsContext = createContext<Permissions | null>(null);

export function PermissionsProvider({ me, children }: { me: Me; children: React.ReactNode }) {
  const value = useMemo<Permissions>(() => {
    const held = new Set(me.permissions);
    const reaches = (permission: string): readonly string[] => me.reaches[permission] ?? [];
    return {
      me,
      has: (permission) => held.has(permission),
      hasAny: (...permissions) => permissions.some((permission) => held.has(permission)),
      reaches,
      reachesAll: (permission) => reaches(permission).includes("all"),
    };
  }, [me]);
  return <PermissionsContext.Provider value={value}>{children}</PermissionsContext.Provider>;
}

export function usePermissions(): Permissions {
  const value = useContext(PermissionsContext);
  if (!value) throw new Error("usePermissions needs a PermissionsProvider above it");
  return value;
}

/** Shows its children only to a user who holds the permission. */
export function Can({ permission, children }: { permission: string; children: React.ReactNode }) {
  return usePermissions().has(permission) ? <>{children}</> : null;
}
