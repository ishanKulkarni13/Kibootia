// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ADMIN_SECTIONS } from "@/components/SiteHeader";
import { FailedEventsScreen } from "@/features/audit/FailedEventsScreen";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

const EXHAUSTED = "11111111-1111-7111-8111-111111111111";
const PENDING = "22222222-2222-7222-8222-222222222222";

function page(items: unknown[], totalElements = items.length) {
  return json({ items, page: { number: 0, size: 25, totalElements, totalPages: totalElements > 0 ? 1 : 0 } });
}

const events = [
  {
    id: EXHAUSTED,
    eventType: "com.kibootia.backend.identity.UserErased",
    listener: "com.kibootia.backend.identity.application.UserEvents#on",
    publishedAt: "2026-11-20T09:00:00Z",
    attempts: 5,
    lastAttemptAt: "2026-11-20T11:00:00Z",
    retriesExhausted: true,
  },
  {
    id: PENDING,
    eventType: "com.example.reports.ReportPurged",
    listener: "com.kibootia.backend.files.application.FileEvents#on",
    publishedAt: "2026-11-24T09:00:00Z",
    attempts: 1,
    lastAttemptAt: "2026-11-24T09:00:05Z",
    retriesExhausted: false,
  },
];

const viewer = meWith(["audit.failed-events.view"]);
const operator = meWith(["audit.failed-events.view", "audit.failed-events.retry"]);

describe("the failed events screen (ADR 0031)", () => {
  it("lists each failed event with its short names, its attempts and whether it needs a person", async () => {
    stubApi(() => page(events));
    renderScreen(<FailedEventsScreen />, viewer);

    const rows = await screen.findAllByTestId("failed-event-row");

    expect(rows).toHaveLength(2);
    expect(within(rows[0]!).getByText("UserErased")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("UserEvents#on")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("5")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("Needs a retry")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("ReportPurged")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Will be retried")).toBeInTheDocument();
  });

  it("keeps the full class names available as tooltips", async () => {
    stubApi(() => page(events));
    renderScreen(<FailedEventsScreen />, viewer);

    const rows = await screen.findAllByTestId("failed-event-row");

    expect(within(rows[0]!).getByText("UserErased")).toHaveAttribute("title", "com.kibootia.backend.identity.UserErased");
  });

  it("says so when nothing has failed", async () => {
    stubApi(() => page([]));
    renderScreen(<FailedEventsScreen />, viewer);

    expect(await screen.findByText("No event has failed.")).toBeInTheDocument();
  });

  it("offers no retry button to someone who may only look", async () => {
    stubApi(() => page(events));
    renderScreen(<FailedEventsScreen />, viewer);

    await screen.findAllByTestId("failed-event-row");

    expect(screen.queryByRole("button", { name: /retry/i })).not.toBeInTheDocument();
  });

  it("retries the event that was chosen, and reloads the list", async () => {
    const api = stubApi((call) => {
      if (call.method === "POST") return new Response(null, { status: 202 });
      return page(events);
    });
    renderScreen(<FailedEventsScreen />, operator);
    await screen.findAllByTestId("failed-event-row");

    await userEvent.click(screen.getByRole("button", { name: /retry userErased/i }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    const post = api.calls.find((call) => call.method === "POST")!;
    expect(post.path).toBe(`/api/v1/failed-events/${EXHAUSTED}/retry`);
    await waitFor(() => expect(api.calls.filter((call) => call.method === "GET").length).toBeGreaterThan(1));
  });

  it("shows the error and keeps the list when the retry is refused", async () => {
    stubApi((call) => (call.method === "POST" ? problem(404, "audit.failed_event_not_found") : page(events)));
    renderScreen(<FailedEventsScreen />, operator);
    await screen.findAllByTestId("failed-event-row");

    await userEvent.click(screen.getByRole("button", { name: /retry userErased/i }));

    await waitFor(() => expect(screen.getAllByTestId("failed-event-row")).toHaveLength(2));
  });

  it("shows an error, not an empty list, when the list cannot be loaded", async () => {
    stubApi(() => problem(500, "platform.internal_error"));
    renderScreen(<FailedEventsScreen />, viewer);

    expect(await screen.findByText("The failed events could not be loaded")).toBeInTheDocument();
    expect(screen.queryByText("No event has failed.")).not.toBeInTheDocument();
  });
});

describe("the administration navigation", () => {
  it("lists the failed events under the permission that the API checks", () => {
    expect(ADMIN_SECTIONS).toContainEqual({
      href: "/admin/failed-events",
      label: "Failed events",
      permission: "audit.failed-events.view",
    });
  });
});
