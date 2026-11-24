// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { ApiError, GENERIC_MESSAGE } from "@/lib/api/problem";

describe("the pager", () => {
  it("counts pages from 1 on screen though the API counts from 0, and says how many there are in all", () => {
    render(<Pager page={0} totalPages={3} totalElements={51} onPage={vi.fn()} />);

    expect(screen.getByText("Page 1 of 3 · 51 in all")).toBeInTheDocument();
  });

  it("cannot go back from the first page, and can go forward", async () => {
    const onPage = vi.fn();
    render(<Pager page={0} totalPages={3} totalElements={51} onPage={onPage} />);

    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(onPage).toHaveBeenCalledWith(1);
  });

  it("cannot go forward from the last page, and can go back", async () => {
    const onPage = vi.fn();
    render(<Pager page={2} totalPages={3} totalElements={51} onPage={onPage} />);

    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Previous" }));

    expect(onPage).toHaveBeenCalledWith(1);
  });

  it("says there is nothing to show for an empty list, and offers neither way", () => {
    render(<Pager page={0} totalPages={0} totalElements={0} onPage={vi.fn()} />);

    expect(screen.getByText("Nothing to show")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("announces a change of page to a screen reader without moving the focus", () => {
    render(<Pager page={1} totalPages={3} totalElements={51} onPage={vi.fn()} />);

    expect(screen.getByText(/Page 2 of 3/)).toHaveAttribute("aria-live", "polite");
  });
});

describe("the error alert", () => {
  it("says what went wrong in words chosen from the problem's code, with the trace to look it up by", () => {
    const error = new ApiError(409, {
      type: "about:blank",
      title: "Conflict",
      status: 409,
      code: "access.role_name_in_use",
      traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
    });
    render(<ErrorAlert error={error} title="The role was not saved" />);

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("The role was not saved")).toBeInTheDocument();
    expect(screen.getByText("Another role already has this name.")).toBeInTheDocument();
    expect(screen.getByText("Reference: 4bf92f3577b34da6a3ce929d0e0e4736")).toBeInTheDocument();
  });

  it("gives the generic words for something that is not an answer of the API, such as a lost connection", () => {
    render(<ErrorAlert error={new TypeError("fetch failed")} />);

    expect(screen.getByText("That did not work")).toBeInTheDocument();
    expect(screen.getByText(GENERIC_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByText(/Reference:/)).not.toBeInTheDocument();
  });

  it("gives the generic words for a code it has no words for, and shows no reference when there is no trace", () => {
    const error = new ApiError(500, { type: "about:blank", title: "Error", status: 500, code: "some.new_code" });
    render(<ErrorAlert error={error} />);

    expect(screen.getByText(GENERIC_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByText(/Reference:/)).not.toBeInTheDocument();
  });
});
