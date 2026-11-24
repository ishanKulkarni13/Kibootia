package com.kibootia.backend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Architecture rule 14 (finding S7 of the security review, milestone 8): hand-written SQL never forgets the tenant.
 * Hibernate's tenant filter does not apply to native SQL, so every statement that names a table with a {@code tenant_id}
 * must say which tenant itself ({@link #check}):
 * <ul>
 * <li>a literal that reads, changes or deletes such a table carries a {@code tenant_id} condition
 * ({@code tenant_id = ...});</li>
 * <li>a literal that inserts into one names the {@code tenant_id} column;</li>
 * <li>a literal that ends in {@code where} and takes its condition from a builder is allowed only in a file whose
 * builder starts with the tenant ({@code new StringBuilder("tenant_id = :tenant")});</li>
 * <li>a statement that deliberately spans every tenant (a data migration) carries the marker comment
 * {@value #EVERY_TENANT} on one of the three lines above it, with a reason, so a reviewer sees it.</li>
 * </ul>
 * The check reads the source text, so it sees string literals and text blocks; SQL put together from constants in
 * other files is out of its reach, which is one more reason to keep each statement in one literal.
 */
public final class TenantSqlRules {

	/** The marker for a statement that deliberately acts on every tenant. */
	public static final String EVERY_TENANT = "// sql: every tenant";

	/** Tables with a tenant_id column. {@code event_publication} and {@code shedlock} have none. */
	static final Set<String> TENANT_TABLES = Set.of("role", "role_grant", "role_permission", "role_permission_seed",
			"role_assignment", "user_profile", "stored_file", "note", "audit_entry", "processed_event");

	private static final Pattern LITERAL = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

	private static final Pattern NAMES_TABLE = Pattern.compile("\\b(from|join|update|into)\\s+(" + String.join("|",
			TENANT_TABLES) + ")\\b");

	private static final Pattern CONDITION = Pattern.compile("tenant_id\\s*(=|in\\b)");

	private TenantSqlRules() {
	}

	/** Every violation in the Java sources under {@code root}, as {@code file:line: statement}. */
	public static List<String> check(Path root) {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter((path) -> path.toString().endsWith(".java")).toList()) {
				violations.addAll(check(root.relativize(file).toString(), Files.readString(file)));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return violations;
	}

	/** The violations in one source text. */
	public static List<String> check(String name, String source) {
		List<String> violations = new ArrayList<>();
		boolean tenantBuilder = source.contains("new StringBuilder(\"tenant_id = :tenant");
		Matcher literal = LITERAL.matcher(source);
		while (literal.find()) {
			String text = (literal.group(1) != null) ? literal.group(1) : literal.group(2);
			String sql = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
			Matcher table = NAMES_TABLE.matcher(sql);
			if (!table.find()) {
				continue;
			}
			boolean insert = table.group(1).equals("into");
			boolean ok = insert ? sql.contains("tenant_id") : CONDITION.matcher(sql).find();
			if (!ok && sql.strip().endsWith("where") && tenantBuilder) {
				ok = true;
			}
			if (!ok && marked(source, literal.start())) {
				ok = true;
			}
			if (!ok) {
				int line = (int) source.substring(0, literal.start()).chars().filter((c) -> c == '\n').count() + 1;
				violations.add(name + ":" + line + ": " + sql.strip());
			}
		}
		return violations;
	}

	/** Whether one of the three lines above the literal carries the every-tenant marker. */
	private static boolean marked(String source, int offset) {
		String before = source.substring(0, offset);
		String[] lines = before.split("\n", -1);
		for (int i = Math.max(0, lines.length - 4); i < lines.length; i++) {
			if (lines[i].contains(EVERY_TENANT)) {
				return true;
			}
		}
		return false;
	}

}
