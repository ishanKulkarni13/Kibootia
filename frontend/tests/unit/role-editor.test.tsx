// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RoleEditor } from "@/features/access/RoleEditor";

import { json, meWith, problem, renderScreen, stubApi, type Call } from "./ui";

const { success, push } = vi.hoisted(() => ({ success: vi.fn(), push: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

const permissions = [
  { name: "access.roles.view", description: "View roles", resourceType: null, reaches: ["all"], userRoleReach: null, sensitive: false },
  { name: "access.roles.manage", description: "Create, change and delete roles", resourceType: null, reaches: ["all"], userRoleReach: null, sensitive: true },
  { name: "identity.users.invite", description: "Invite users by email", resourceType: null, reaches: ["all"], userRoleReach: null, sensitive: false },
  {
    name: "notes.notes.view",
    description: "View notes",
    resourceType: "notes.note",
    reaches: ["all", "relation:owner"],
    userRoleReach: "relation:owner",
    sensitive: false,
  },
];
const catalogue = { permissions, denyGrantsEnabled: false };

const all = (action: string) => ({ action, reach: "all", effect: "ALLOW" });

function role(overrides: Record<string, unknown> = {}) {
  return {
    id: "r-1",
    name: "Reviewers",
    description: "Review things",
    builtIn: null,
    grants: [all("access.roles.view")],
    assignedUsers: 2,
    version: 4,
    createdAt: "2026-11-01T10:00:00Z",
    updatedAt: "2026-11-01T10:00:00Z",
    ...overrides,
  };
}

function api(current: ReturnType<typeof role>, onSave?: (call: Call) => Response) {
  return stubApi((call) => {
    if (call.path === "/api/v1/permissions") return json(catalogue);
    if (call.method === "GET") return json(current);
    return onSave ? onSave(call) : json({ ...current, version: current.version + 1 });
  });
}

const manager = () => meWith(["access.roles.view", "access.roles.manage", "notes.notes.view/relation:owner"]);

type Grants = { action: string; reach: string; effect?: string }[];
const sorted = (grants: Grants) => [...grants].sort((a, b) => a.action.localeCompare(b.action));

describe("the role editor", () => {
  beforeEach(() => {
    success.mockClear();
    push.mockClear();
  });

  it("shows the administrator role read-only: it holds every permission and cannot be changed", async () => {
    api(role({ builtIn: "ADMIN", name: "Administrator", grants: permissions.map((permission) => all(permission.name)) }));
    renderScreen(<RoleEditor roleId="r-1" />, meWith(["access.roles.view", "access.roles.manage", "identity.users.invite"]));

    expect(await screen.findByText(/cannot be changed or deleted/)).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /View roles/ })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Save role" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete role" })).not.toBeInTheDocument();
  });

  it("lets someone who may only view roles look but not change", async () => {
    api(role());
    renderScreen(<RoleEditor roleId="r-1" />, meWith(["access.roles.view"]));

    expect(await screen.findByRole("heading", { name: "Reviewers" })).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Save role" })).not.toBeInTheDocument();
    expect(screen.getByText(/look at this role but not change it/)).toBeInTheDocument();
  });

  it("does not let an editor add a permission they do not hold themselves, though they may remove one", async () => {
    api(role({ grants: [all("access.roles.view"), all("identity.users.invite")] }));
    renderScreen(<RoleEditor roleId="r-1" />, manager());

    await screen.findByRole("heading", { name: "Reviewers" });

    // The editor lacks identity.users.invite but the role already has it: it can be taken off, not put back.
    expect(screen.getByRole("checkbox", { name: /Invite users/ })).toBeEnabled();
    await userEvent.click(screen.getByRole("checkbox", { name: /Invite users/ }));
    expect(screen.getByRole("checkbox", { name: /Invite users/ })).toBeDisabled();
  });

  it("gives a permission only as far as the editor's own grant reaches", async () => {
    const stub = api(role());
    renderScreen(<RoleEditor roleId="r-1" />, manager());
    await screen.findByRole("heading", { name: "Reviewers" });

    // The editor views only their own notes, so ticking it offers that reach and not "every record".
    await userEvent.click(screen.getByRole("checkbox", { name: /View notes/ }));
    const reach = screen.getByRole("combobox", { name: "How far: View notes" });
    expect(reach).toHaveValue("relation:owner");
    expect(screen.getByRole("option", { name: "Every record" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Save role" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Role saved"));
    const save = stub.calls.find((call) => call.method === "PUT");
    expect(sorted((save?.body as { grants: Grants }).grants)).toEqual([
      { action: "access.roles.view", reach: "all", effect: "ALLOW" },
      { action: "notes.notes.view", reach: "relation:owner", effect: "ALLOW" },
    ]);
  });

  it("lets an editor who holds a permission for every record choose its reach", async () => {
    const stub = api(role());
    renderScreen(<RoleEditor roleId="r-1" />, meWith(["access.roles.view", "access.roles.manage", "notes.notes.view"]));
    await screen.findByRole("heading", { name: "Reviewers" });

    await userEvent.click(screen.getByRole("checkbox", { name: /View notes/ }));
    expect(screen.getByRole("combobox", { name: "How far: View notes" })).toHaveValue("all");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "How far: View notes" }), "relation:owner");
    await userEvent.click(screen.getByRole("button", { name: "Save role" }));

    await waitFor(() => expect(success).toHaveBeenCalled());
    const save = stub.calls.find((call) => call.method === "PUT");
    expect((save?.body as { grants: Grants }).grants).toContainEqual({ action: "notes.notes.view", reach: "relation:owner", effect: "ALLOW" });
  });

  it("keeps a role's deny grants as they are when it is saved, and says it has them", async () => {
    const deny = { action: "identity.users.invite", reach: "all", effect: "DENY" };
    const stub = api(role({ grants: [all("access.roles.view"), deny] }));
    renderScreen(<RoleEditor roleId="r-1" />, manager());
    await screen.findByRole("heading", { name: "Reviewers" });

    expect(screen.getByText(/also has a deny grant/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /Invite users/ })).not.toBeChecked();
    await userEvent.click(screen.getByRole("button", { name: "Save role" }));

    await waitFor(() => expect(success).toHaveBeenCalled());
    const save = stub.calls.find((call) => call.method === "PUT");
    expect((save?.body as { grants: Grants }).grants).toContainEqual(deny);
  });

  it("saves with the version it started from, so a change made meanwhile is not overwritten", async () => {
    const stub = api(role());
    renderScreen(<RoleEditor roleId="r-1" />, manager());
    await screen.findByRole("heading", { name: "Reviewers" });

    await userEvent.clear(screen.getByLabelText("Name"));
    await userEvent.type(screen.getByLabelText("Name"), "Auditors");
    await userEvent.click(screen.getByRole("checkbox", { name: /Create, change and delete roles/ }));
    await userEvent.click(screen.getByRole("button", { name: "Save role" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Role saved"));
    const save = stub.calls.find((call) => call.method === "PUT");
    expect(save).toMatchObject({
      path: "/api/v1/roles/r-1",
      body: { name: "Auditors", description: "Review things", version: 4 },
    });
    expect(sorted((save?.body as { grants: Grants }).grants)).toEqual([
      { action: "access.roles.manage", reach: "all", effect: "ALLOW" },
      { action: "access.roles.view", reach: "all", effect: "ALLOW" },
    ]);
  });

  it("says so under the name when another role has it, and when someone else changed the role meanwhile", async () => {
    let answer = problem(409, "access.role_name_in_use");
    api(role(), () => answer);
    renderScreen(<RoleEditor roleId="r-1" />, manager());
    await screen.findByRole("heading", { name: "Reviewers" });

    await userEvent.click(screen.getByRole("button", { name: "Save role" }));
    expect(await screen.findByText("Another role already has this name.")).toBeInTheDocument();

    answer = problem(409, "platform.concurrent_modification");
    await userEvent.click(screen.getByRole("button", { name: "Save role" }));
    expect(await screen.findByText(/Someone else changed this while you were editing/)).toBeInTheDocument();
  });

  it("creates a role without asking for one that does not exist yet, and opens it afterwards", async () => {
    const stub = stubApi((call) => {
      if (call.path === "/api/v1/permissions") return json(catalogue);
      return json(role({ id: "r-new", name: "Inviters", grants: [], version: 0 }), 201);
    });
    renderScreen(<RoleEditor />, manager());

    await userEvent.type(await screen.findByLabelText("Name"), "Inviters");
    await userEvent.click(screen.getByRole("button", { name: "Create role" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/roles/r-new"));
    expect(stub.calls.find((call) => call.method === "POST")).toMatchObject({
      path: "/api/v1/roles",
      body: { name: "Inviters", description: "", grants: [] },
    });
    expect(stub.calls.filter((call) => call.method === "GET").map((call) => call.path)).toEqual(["/api/v1/permissions"]);
  });

  it("checks the name before sending", async () => {
    const stub = api(role());
    renderScreen(<RoleEditor roleId="r-1" />, manager());
    await screen.findByRole("heading", { name: "Reviewers" });

    await userEvent.clear(screen.getByLabelText("Name"));
    await userEvent.type(screen.getByLabelText("Name"), "a@b");
    await userEvent.click(screen.getByRole("button", { name: "Save role" }));

    expect(await screen.findByText(/Use letters, digits, spaces/)).toBeInTheDocument();
    expect(stub.calls.some((call) => call.method === "PUT")).toBe(false);
  });
});
