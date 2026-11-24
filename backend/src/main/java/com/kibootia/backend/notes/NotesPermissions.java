package com.kibootia.backend.notes;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceType;

/**
 * The actions of the notes feature (ADR 0044, ADR 0054). A note has an owner, so viewing, changing and deleting are
 * actions on a record: a role grants them either for every note of the tenant or for the holder's own notes. Every user
 * holds them for their own notes. Creating, restoring and purging are actions on the collection.
 */
@Component
public class NotesPermissions implements PermissionProvider {

	/** The resource type of a note, as the decision point sees it (ADR 0055). */
	public static final String NOTE = "notes.note";

	public static final String CREATE = "notes.notes.create";

	public static final String VIEW = "notes.notes.view";

	public static final String UPDATE = "notes.notes.update";

	public static final String DELETE = "notes.notes.delete";

	public static final String RESTORE = "notes.notes.restore";

	public static final String PURGE = "notes.notes.purge";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.onCollection(CREATE, "Create notes").forEveryUser(),
				Permission.onRecord(VIEW, NOTE, "View notes").forEveryUser(Reach.owner()),
				Permission.onRecord(UPDATE, NOTE, "Change notes").forEveryUser(Reach.owner()),
				Permission.onRecord(DELETE, NOTE, "Delete notes").forEveryUser(Reach.owner()),
				Permission.onCollection(RESTORE, "Restore deleted notes"),
				Permission.onCollection(PURGE, "Permanently remove notes whose retention period has passed").asSensitive());
	}

	@Override
	public List<ResourceType> resourceTypes() {
		return List.of(ResourceType.of(NOTE, "A note", ResourceRef.OWNER));
	}

}
