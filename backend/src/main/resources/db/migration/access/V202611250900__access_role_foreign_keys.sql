-- Foreign keys inside the access module's own tables (ADR 0059): a permission or an assignment cannot name a role that
-- does not exist. No key crosses a module boundary: role_assignment.user_id stays a plain ID, because the user profile
-- belongs to the identity module (ADR 0039, ADR 0024).
--
-- A row whose role is already gone grants nothing (every read joins the role), so such a row is removed first. The role
-- purge deletes role_permission rows before the role, and a role that users still hold cannot be deleted, so neither
-- statement removes anything in an installation that only used the application.
DELETE rp FROM role_permission rp LEFT JOIN role r ON r.id = rp.role_id WHERE r.id IS NULL;
DELETE ra FROM role_assignment ra LEFT JOIN role r ON r.id = ra.role_id WHERE r.id IS NULL;

ALTER TABLE role_permission
    ADD CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id);
ALTER TABLE role_assignment
    ADD CONSTRAINT fk_role_assignment_role FOREIGN KEY (role_id) REFERENCES role (id);
