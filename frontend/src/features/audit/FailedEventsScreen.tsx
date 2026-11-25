"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useFailedEvents, useRetryFailedEvent } from "@/features/audit/api";
import { Can } from "@/features/identity/permissions";

/** The part of a class name people recognise: `com.kibootia.backend.notes.NotePurged` is shown as `NotePurged`. */
function shortName(name: string): string {
  const afterPackage = name.slice(name.lastIndexOf(".") + 1);
  return afterPackage.split("$").join(".");
}

/**
 * Events whose delivery failed (ADR 0031): a change that was made but whose follow-up, such as an email, a deletion in
 * Keycloak or a file removal, did not complete. The system retries on its own a few times; what is listed here has failed
 * at least once, and one whose retries are used up needs a person to retry it. Needs audit.failed-events.view; the retry
 * button needs audit.failed-events.retry. The list never shows what an event carries, only which event and which handler.
 */
export function FailedEventsScreen() {
  const [page, setPage] = useState(0);
  const events = useFailedEvents({ page, size: 25 });
  const retry = useRetryFailedEvent();

  return (
    <section aria-labelledby="failed-events-heading">
      <h1 id="failed-events-heading" className="text-2xl font-semibold tracking-tight">
        Failed events
      </h1>
      <p className="mt-2 max-w-prose text-sm text-muted-foreground">
        Follow-up work that did not complete, such as an email, a removal in Keycloak or the deletion of a file. The system
        retries it on its own a few times. One marked &quot;needs a retry&quot; has used them up and waits for you. Events
        are removed from this list, and an entry recorded in the audit log, after the retention period.
      </p>

      <div className="mt-6">
        {events.isError && <ErrorAlert error={events.error} title="The failed events could not be loaded" />}
        {events.isPending && <Skeleton className="h-40 w-full" aria-label="Loading the failed events" />}
        {events.data && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Published</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Handler</TableHead>
                  <TableHead>Attempts</TableHead>
                  <TableHead>Last attempt</TableHead>
                  <TableHead>State</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.data.items.map((event) => (
                  <TableRow key={event.id} data-testid="failed-event-row">
                    <TableCell className="whitespace-nowrap">
                      <time dateTime={event.publishedAt}>{new Date(event.publishedAt).toLocaleString()}</time>
                    </TableCell>
                    <TableCell>
                      <code className="text-xs" title={event.eventType}>
                        {shortName(event.eventType)}
                      </code>
                    </TableCell>
                    <TableCell>
                      <code className="text-xs" title={event.listener}>
                        {shortName(event.listener)}
                      </code>
                    </TableCell>
                    <TableCell>{event.attempts}</TableCell>
                    <TableCell className="whitespace-nowrap">
                      <time dateTime={event.lastAttemptAt}>{new Date(event.lastAttemptAt).toLocaleString()}</time>
                    </TableCell>
                    <TableCell>
                      {event.retriesExhausted ? <Badge variant="destructive">Needs a retry</Badge> : <Badge variant="secondary">Will be retried</Badge>}
                    </TableCell>
                    <TableCell className="text-right">
                      <Can permission="audit.failed-events.retry">
                        <Button
                          size="sm"
                          variant="outline"
                          aria-label={`Retry ${shortName(event.eventType)}`}
                          disabled={retry.isPending && retry.variables === event.id}
                          onClick={() =>
                            retry.mutate(event.id, {
                              onSuccess: () => toast.success("The event is being delivered again"),
                              onError: () => toast.error("The retry could not be started"),
                            })
                          }
                        >
                          Retry
                        </Button>
                      </Can>
                    </TableCell>
                  </TableRow>
                ))}
                {events.data.items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center text-muted-foreground">
                      No event has failed.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <Pager
              page={events.data.page.number}
              totalPages={events.data.page.totalPages}
              totalElements={events.data.page.totalElements}
              onPage={setPage}
            />
          </>
        )}
      </div>
    </section>
  );
}
