package com.kibootia.backend.notes.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.notes.NotesPermissions;
import com.kibootia.backend.platform.authorization.Resource;
import com.kibootia.backend.platform.authorization.ResourceRef;

/**
 * A note as the use cases see it: plain data, with no persistence or HTTP concerns (ADR 0004). It tells the decision
 * point what it is, who owns it and which tenant it belongs to.
 *
 * @param attachmentFileId the stored picture of the note, kept by the files module, or null when there is none
 */
public record Note(UUID id, UUID tenantId, UUID ownerId, String title, String body, long version, Instant createdAt,
		Instant updatedAt, UUID attachmentFileId) implements Resource {

	@Override
	public ResourceRef resourceRef() {
		return ResourceRef.of(NotesPermissions.NOTE, this.id, this.tenantId).ownedBy(this.ownerId);
	}

}
