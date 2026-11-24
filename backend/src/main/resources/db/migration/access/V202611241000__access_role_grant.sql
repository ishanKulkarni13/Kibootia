-- The grant model (ADR 0054, milestone 8): a role holds grants, each an action, how far it reaches, and whether it
-- allows or denies. It replaces role_permission, where a permission name ending in "-any" stood for "every record".
--
-- The rows of role_permission are converted by the application at startup, not here: whether a stored "x" meant "my own
-- records" depends on which actions are declared on a resource type with an owner, and only the running application
-- knows that. role_permission is therefore kept until a later migration drops it (milestone 8, section 7).
CREATE TABLE role_grant (
    tenant_id BINARY(16)   NOT NULL,
    role_id   BINARY(16)   NOT NULL,
    action    VARCHAR(100) NOT NULL,
    -- "all", or "relation:<name>" such as "relation:owner".
    reach     VARCHAR(100) NOT NULL,
    effect    VARCHAR(5)   NOT NULL,
    PRIMARY KEY (tenant_id, role_id, action, reach, effect),
    -- Inside one module's own tables a foreign key is kept (ADR 0059): purging a role takes its grants with it.
    CONSTRAINT fk_role_grant_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT ck_role_grant_effect CHECK (effect IN ('ALLOW', 'DENY'))
);
