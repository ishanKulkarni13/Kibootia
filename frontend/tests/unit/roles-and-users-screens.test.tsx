// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { RolesScreen } from "@/features/access/RolesScreen";
import { UsersScreen } from "@/features/identity/UsersScreen";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

function paged(items: unknown[], number = 0, totalPages = 1, totalElements = items.length) {
  return json({ items, page: { number, size: 20, totalElements, totalPages } });
}

const role = (overrides: Record<string, unknown> = {}) => ({
  id: "r-1",
  name: "Reviewers",
  description: "Review things",
  builtIn: null,
  grants: [{ action: "access.roles.view", reach: "all", effect: "ALLOW" }],
  assignedUsers: 2,
  version: 0,
  createdAt: "2026-11-01T10:00:00Z",
  updatedAt: "2026-11-01T10:00:00Z",
  ...overrides,
});

const user = (overrides: Record<string, unknown> = {}) => ({
  id: "22222222-2222-7222-8222-222222222222",
  username: "bob",
  email: "bob@kibootia.test",
  name: "Bob Example",
  state: "ACTIVE",
  emailVerified: true,
  erasureBegun: false,
  avatarVersion: null,
  roles: [{ id: "r-user", name: "User", builtIn: "USER" }],
  version: 0,
  createdAt: "2026-11-01T10:00:00Z",
  updatedAt: "2026-11-01T10:00:00Z",
  ...overrides,
});

describe("the roles screen", () => {
  it("lists each role as a link to it, with its description, how many permissions it has and how many users hold it", async () => {
    stubApi(() =>
      paged([
        role(),
        role({ id: "r-2", name: "Administrator", description: "Everything", builtIn: "ADMIN", grants: [], assignedUsers: 7 }),
      ]),
    );
    renderScreen(<RolesScreen />, meWith(["access.roles.view"]));

    const rows = await screen.findAllByTestId("role-row");

    expect(rows).toHaveLength(2);
    const reviewers = within(rows[0]!);
    expect(reviewers.getByRole("link", { name: "Reviewers" })).toHaveAttribute("href", "/admin/roles/r-1");
    expect(reviewers.getByText("Review things")).toBeInTheDocument();
    expect(reviewers.getByText("1")).toBeInTheDocument();
    expect(reviewers.getByText("2")).toBeInTheDocument();
    expect(reviewers.queryByText("built in")).not.toBeInTheDocument();
    expect(within(rows[1]!).getByText("built in")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("7")).toBeInTheDocument();
  });

  it("offers a new role only to someone who may manage roles", async () => {
    stubApi(() => paged([role()]));
    const { unmount } = renderScreen(<RolesScreen />, meWith(["access.roles.view"]));
    await screen.findAllByTestId("role-row");
    expect(screen.queryByRole("link", { name: "New role" })).not.toBeInTheDocument();
    unmount();

    renderScreen(<RolesScreen />, meWith(["access.roles.view", "access.roles.manage"]));
    expect(await screen.findByRole("link", { name: "New role" })).toHaveAttribute("href", "/admin/roles/new");
  });

  it("says the roles could not be loaded, rather than showing an empty table", async () => {
    stubApi(() => problem(500, "platform.internal_error"));
    renderScreen(<RolesScreen />, meWith(["access.roles.view"]));

    expect(await screen.findByText("The roles could not be loaded")).toBeInTheDocument();
    expect(screen.queryByTestId("role-row")).not.toBeInTheDocument();
  });
});

describe("the users screen", () => {
  const viewer = () => meWith(["identity.profiles.view"]);

  it("lists each user with their email, state and roles, and links to the user", async () => {
    stubApi(() =>
      paged([user(), user({ id: "33333333-3333-7333-8333-333333333333", name: "Carol Example", state: "DISABLED", roles: [] })]),
    );
    renderScreen(<UsersScreen />, viewer());

    const rows = await screen.findAllByTestId("user-row");

    expect(rows).toHaveLength(2);
    const bob = within(rows[0]!);
    expect(bob.getByRole("link", { name: "Bob Example" })).toHaveAttribute("href", "/admin/users/22222222-2222-7222-8222-222222222222");
    expect(bob.getByText("bob@kibootia.test")).toBeInTheDocument();
    expect(bob.getByText("Active")).toBeInTheDocument();
    expect(bob.getByText("User")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Disabled")).toBeInTheDocument();
  });

  it("asks the API for users sorted by name, 20 to a page, with no filter to begin with", async () => {
    const api = stubApi(() => paged([user()]));
    renderScreen(<UsersScreen />, viewer());
    await screen.findAllByTestId("user-row");

    const query = api.calls[0]!.query;
    expect(query.get("size")).toBe("20");
    expect(query.get("page")).toBe("0");
    expect(query.get("sort")).toBe("name,asc");
    expect(query.get("q")).toBeNull();
    expect(query.get("state")).toBeNull();
  });

  it("searches for the text without its surrounding spaces, and starts again from the first page", async () => {
    const api = stubApi(() => paged([user()], 0, 3, 41));
    renderScreen(<UsersScreen />, viewer());
    await screen.findAllByTestId("user-row");
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(api.calls.at(-1)!.query.get("page")).toBe("1"));

    await userEvent.type(screen.getByLabelText("Search"), "  carol  ");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => expect(api.calls.at(-1)!.query.get("q")).toBe("carol"));
    expect(api.calls.at(-1)!.query.get("page")).toBe("0");
  });

  it("goes to the next page when asked", async () => {
    const api = stubApi(() => paged([user()], 0, 2, 25));
    renderScreen(<UsersScreen />, viewer());
    await screen.findAllByTestId("user-row");

    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => expect(api.calls.at(-1)!.query.get("page")).toBe("1"));
  });

  it("says no users match when the list is empty", async () => {
    stubApi(() => paged([]));
    renderScreen(<UsersScreen />, viewer());

    expect(await screen.findByText("No users match.")).toBeInTheDocument();
  });

  it("offers the invitation only to someone who may invite", async () => {
    stubApi(() => paged([user()]));
    const { unmount } = renderScreen(<UsersScreen />, viewer());
    await screen.findAllByTestId("user-row");
    expect(screen.queryByRole("button", { name: /invite/i })).not.toBeInTheDocument();
    unmount();

    renderScreen(<UsersScreen />, meWith(["identity.profiles.view", "identity.users.invite"]));
    expect(await screen.findByRole("button", { name: /invite/i })).toBeInTheDocument();
  });

  it("says the users could not be loaded, rather than showing an empty table", async () => {
    stubApi(() => problem(500, "platform.internal_error"));
    renderScreen(<UsersScreen />, viewer());

    expect(await screen.findByText("The users could not be loaded")).toBeInTheDocument();
    expect(screen.queryByText("No users match.")).not.toBeInTheDocument();
  });
});
