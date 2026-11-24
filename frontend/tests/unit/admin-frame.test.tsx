// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AdminFrame } from "@/features/identity/AdminFrame";
import { PermissionsProvider } from "@/features/identity/permissions";

import { meWith } from "./ui";

const path = vi.hoisted(() => ({ current: "/admin/users" }));
vi.mock("next/navigation", () => ({ usePathname: () => path.current }));

function frame(permissions: string[], at: string) {
  path.current = at;
  render(
    <PermissionsProvider me={meWith(permissions)}>
      <AdminFrame>
        <p>section content</p>
      </AdminFrame>
    </PermissionsProvider>,
  );
}

describe("the administration frame guards each section (usability only: the API refuses the rest)", () => {
  it("shows a section the user may see", () => {
    frame(["identity.profiles.view"], "/admin/users");
    expect(screen.getByText("section content")).toBeInTheDocument();
  });

  it("refuses a section the user may not see, with a heading, instead of rendering it", () => {
    frame(["audit.entries.view"], "/admin/users");
    expect(screen.getByRole("heading", { level: 1, name: "You do not have access to users" })).toBeInTheDocument();
    expect(screen.queryByText("section content")).not.toBeInTheDocument();
    // The sections the user may see are still offered.
    expect(screen.getByRole("link", { name: "Audit log" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Users" })).not.toBeInTheDocument();
  });

  it("covers the pages inside a section, such as one user's page", () => {
    frame(["access.roles.view"], "/admin/users/0190a5c0-0000-7000-8000-000000000001");
    expect(screen.getByRole("heading", { name: "You do not have access to users" })).toBeInTheDocument();
    expect(screen.queryByText("section content")).not.toBeInTheDocument();
  });

  it("tells a user with no section at all that they have no access to administration", () => {
    frame([], "/admin/roles");
    expect(screen.getByRole("heading", { name: "You do not have access to administration" })).toBeInTheDocument();
  });
});
