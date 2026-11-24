package com.kibootia.backend.platform.jobs;

/**
 * Implemented by a module that has data to remove once its retention period has passed: soft-deleted rows, audit
 * entries, completed events (ADR 0034, ADR 0046). The retention job calls every implementation once a day, for each
 * tenant, in its own transaction, as the system.
 *
 * <p>An implementation must not remove other tenants' data: entity queries are already filtered by the bound tenant,
 * native SQL needs an explicit {@code tenant_id} condition.
 */
public interface RetentionPurgeable {

	/** A short stable name for the logs, for example {@code audit-entries}. */
	String name();

	/** Removes what has expired and returns how many rows it removed. */
	int purge();

	/**
	 * Whether {@link #purge()} opens its own short transactions, in which case the job calls it with none open. A purge that
	 * calls another system, such as object storage, must say so: no database transaction may be open across such a call
	 * (ADR 0058). The default, false, is a purge that only touches the database, which the job runs in one transaction.
	 */
	default boolean ownsTransactions() {
		return false;
	}

}
