package com.kibootia.backend.platform.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.GrantedPermissions;
import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization through the real context: {@code @RequiresPermission} enforced by method security, the decision point
 * from the {@code access} module, record checks, access filters, module rules, decision records, the system principal
 * and the 403 the API returns. Each protected operation has an allowed and a denied case (verification standard).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, GrantedPermissions.class, AuthorizationIT.Declares.class,
		AuthorizationIT.ProbeService.class, AuthorizationIT.ProbeController.class, AuthorizationIT.LockedItems.class })
@ExtendWith(OutputCaptureExtension.class)
class AuthorizationIT {

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	/** An item the module rule below always refuses to change. */
	static final UUID LOCKED = UUID.fromString("0190a5c0-0000-7000-8000-00000000100c");

	static final String ITEM = "probe.item";

	static final String READ = "probe.items.read";

	static final String UPDATE = "probe.items.update";

	static final String PURGE = "probe.items.purge";

	record Item(UUID id, UUID ownerId, UUID tenantId) implements Resource {

		Item(UUID ownerId, UUID tenantId) {
			this(UUID.randomUUID(), ownerId, tenantId);
		}

		@Override
		public ResourceRef resourceRef() {
			return ResourceRef.of(ITEM, this.id, this.tenantId).ownedBy(this.ownerId);
		}

	}

	@Component
	static class Declares implements PermissionProvider {

		@Override
		public List<Permission> permissions() {
			return List.of(Permission.onCollection(READ, "Read items").forEveryUser(),
					Permission.onRecord(UPDATE, ITEM, "Change items").forEveryUser(Reach.owner()),
					Permission.onCollection(PURGE, "Purge items").asSensitive());
		}

		@Override
		public List<ResourceType> resourceTypes() {
			return List.of(ResourceType.of(ITEM, "An item", ResourceRef.OWNER));
		}

	}

	/** A module rule: a locked item cannot be changed, whatever the grants say. */
	@Component
	static class LockedItems implements ResourceRule {

		@Override
		public String name() {
			return "probe.locked-items";
		}

		@Override
		public boolean appliesTo(String resourceType, String action) {
			return ITEM.equals(resourceType) && UPDATE.equals(action);
		}

		@Override
		public Optional<String> objection(Principal principal, String action, ResourceRef resource) {
			return LOCKED.equals(resource.id()) ? Optional.of("the item is locked") : Optional.empty();
		}

	}

	@Service
	static class ProbeService {

		private final Authorization authorization;

		ProbeService(Authorization authorization) {
			this.authorization = authorization;
		}

		@RequiresPermission(READ)
		public String read() {
			return "read";
		}

		@RequiresPermission(PURGE)
		public String purge() {
			return "purged";
		}

		public String update(Item item) {
			this.authorization.require(UPDATE, item);
			return "updated";
		}

		public AccessFilter filterOfUpdates() {
			return this.authorization.requireFilter(UPDATE);
		}

		public AccessFilter filterOfPurges() {
			return this.authorization.requireFilter(PURGE);
		}

		public Item openItem(Optional<Item> item) {
			return this.authorization.requireVisible(UPDATE, item, () -> ApiException.notFound("probe.not_found", "No item"));
		}

		@NoAuthorization("returns nothing about anyone")
		public String open() {
			return "open";
		}

		public UUID whoIsCalling() {
			return this.authorization.currentUserId();
		}

	}

	@RestController
	@RequestMapping("/api/v1/probe-auth")
	static class ProbeController {

		private final ProbeService service;

		ProbeController(ProbeService service) {
			this.service = service;
		}

		@GetMapping("/read")
		String read() {
			return this.service.read();
		}

		@GetMapping("/purge")
		String purge() {
			return this.service.purge();
		}

	}

	@Autowired
	ProbeService service;

	@Autowired
	Authorization authorization;

	@Autowired
	MockMvc mvc;

	@Autowired
	MeterRegistry meters;

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
		GrantedPermissions.reset();
	}

	private static void signInAs(UUID user) {
		Jwt token = Jwt.withTokenValue("t")
			.header("alg", "none")
			.subject(user.toString())
			.claim("email_verified", true)
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
	}

	private static <T> T inTenant(java.util.concurrent.Callable<T> work) {
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT, work);
	}

	private double counted(String outcome, String reason, String action) {
		Counter counter = this.meters.find(DecisionRecorder.METRIC)
			.tag("outcome", outcome)
			.tag("reason", reason)
			.tag("action", action)
			.counter();
		return (counter == null) ? 0 : counter.count();
	}

	private static List<String> decisionRecords(CapturedOutput output) {
		return output.getAll().lines().filter((line) -> line.contains(DecisionRecorder.LOGGER)).toList();
	}

	// --- the checks, unchanged from contract version 1 ---------------------------------------------------------------

	@Test
	@DisplayName("a permission the user role holds is allowed")
	void allowedPermission() {
		signInAs(ALICE);
		assertThat(inTenant(service::read)).isEqualTo("read");
	}

	@Test
	@DisplayName("a permission nobody was granted is denied")
	void deniedPermission() {
		signInAs(ALICE);
		assertThatThrownBy(() -> inTenant(service::purge)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a method marked @NoAuthorization needs no permission")
	void noAuthorizationMarker() {
		signInAs(ALICE);
		assertThat(inTenant(service::open)).isEqualTo("open");
	}

	@Test
	@DisplayName("a record the caller owns is allowed, someone else's is denied")
	void recordOwnership() {
		signInAs(ALICE);
		assertThat(inTenant(() -> service.update(new Item(ALICE, TenantContext.DEFAULT_TENANT)))).isEqualTo("updated");
		assertThatThrownBy(() -> inTenant(() -> service.update(new Item(BOB, TenantContext.DEFAULT_TENANT))))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a record of another tenant is denied even to its owner")
	void recordOfAnotherTenant() {
		signInAs(ALICE);
		assertThatThrownBy(() -> inTenant(() -> service.update(new Item(ALICE, OTHER_TENANT))))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a list is limited to the caller's own records with a grant reaching only their own")
	void listFilter() {
		signInAs(ALICE);
		assertThat(inTenant(service::filterOfUpdates)).isEqualTo(AccessFilter.relatedTo(ResourceRef.OWNER, ALICE));
		assertThatThrownBy(() -> inTenant(service::filterOfPurges)).isInstanceOf(AccessDeniedException.class);
		GrantedPermissions.grant(UPDATE);
		assertThat(inTenant(service::filterOfUpdates)).isEqualTo(AccessFilter.EVERYTHING);
	}

	@Test
	@DisplayName("the system may do anything, in the tenant it runs in, and says why")
	void systemCaller() {
		signInAs(ALICE);
		String result = inTenant(() -> authorization.runAsSystem("test: purge expired items", service::purge));
		assertThat(result).isEqualTo("purged");
		assertThatThrownBy(() -> inTenant(service::purge)).as("system authority ends with the block")
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("acting as the system needs a reason and a bound tenant")
	void systemNeedsReasonAndTenant() {
		assertThatIllegalArgumentException().isThrownBy(() -> inTenant(() -> {
			authorization.runAsSystem(" ", (Runnable) () -> {
			});
			return null;
		}));
		assertThatThrownBy(() -> authorization.runAsSystem("no tenant", () -> {
		})).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("with nobody signed in, everything is denied")
	void nobodySignedIn() {
		assertThatThrownBy(() -> inTenant(service::read)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a token whose subject is not a user ID is denied")
	void subjectIsNotAUser() {
		Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject("not-a-uuid").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
		assertThatThrownBy(() -> inTenant(service::read)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a call outside any tenant is denied")
	void noTenant() {
		signInAs(ALICE);
		assertThatThrownBy(service::read).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("the current user ID comes from the token")
	void currentUserId() {
		signInAs(BOB);
		assertThat(inTenant(service::whoIsCalling)).isEqualTo(BOB);
	}

	@Test
	@DisplayName("over HTTP an allowed call is 200 and a denied one is 403 Problem Details")
	void overHttp() throws Exception {
		var alice = jwt().jwt((token) -> token.subject(ALICE.toString()).claim("email_verified", true));
		this.mvc.perform(get("/api/v1/probe-auth/read").with(alice)).andExpect(status().isOk());
		this.mvc.perform(get("/api/v1/probe-auth/purge").with(alice))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("platform.forbidden"))
			.andExpect(jsonPath("$.detail").value("Access is denied"));
	}

	@Test
	@DisplayName("over HTTP an unverified caller is refused with email_not_verified, not a plain forbidden")
	void overHttpUnverified() throws Exception {
		var unverified = jwt().jwt((token) -> token.subject(ALICE.toString()).claim("email_verified", false));
		this.mvc.perform(get("/api/v1/probe-auth/read").with(unverified))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		this.mvc.perform(get("/api/v1/probe-auth/read").with(jwt().jwt((token) -> token.subject(ALICE.toString()))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
	}

	@Test
	@DisplayName("requireVerified lets a verified caller through and refuses an unverified one")
	void requireVerified() {
		signInAs(ALICE);
		inTenant(() -> {
			authorization.requireVerified();
			return null;
		});
		Jwt unverified = Jwt.withTokenValue("t").header("alg", "none").subject(ALICE.toString()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(unverified));
		assertThatThrownBy(() -> inTenant(() -> {
			authorization.requireVerified();
			return null;
		})).isInstanceOf(EmailNotVerifiedException.class);
	}

	@Test
	@DisplayName("over HTTP without a token is 401")
	void overHttpUnauthenticated() throws Exception {
		this.mvc.perform(get("/api/v1/probe-auth/read")).andExpect(status().isUnauthorized());
	}

	// --- new in contract version 2 -------------------------------------------------------------------------------------

	@Test
	@DisplayName("a decision carries its reason and what decided it")
	void decisionsCarryReasons() {
		signInAs(ALICE);
		assertThat(inTenant(() -> authorization.decide(UPDATE, new Item(ALICE, TenantContext.DEFAULT_TENANT))))
			.isEqualTo(Decision.of(Reason.GRANTED, UPDATE + "/relation:owner"));
		assertThat(inTenant(() -> authorization.decide(UPDATE, new Item(BOB, TenantContext.DEFAULT_TENANT))))
			.isEqualTo(Decision.of(Reason.OUT_OF_REACH, UPDATE + "/relation:owner"));
		assertThat(inTenant(() -> authorization.decide(PURGE))).isEqualTo(Decision.of(Reason.NO_GRANT));
	}

	@Test
	@DisplayName("a module rule narrows what grants allow, even for a holder of a grant reaching every record")
	void moduleRuleNarrows() {
		signInAs(ALICE);
		GrantedPermissions.grant(UPDATE);
		Item locked = new Item(LOCKED, ALICE, TenantContext.DEFAULT_TENANT);
		assertThat(inTenant(() -> authorization.decide(UPDATE, locked)))
			.isEqualTo(Decision.of(Reason.REFUSED_BY_RULE, "probe.locked-items"));
		assertThatThrownBy(() -> inTenant(() -> service.update(locked))).isInstanceOf(AccessDeniedException.class);
		assertThat(inTenant(() -> service.update(new Item(BOB, TenantContext.DEFAULT_TENANT)))).isEqualTo("updated");
	}

	@Test
	@DisplayName("403 without any grant for the action, decided before the record; 404 alike for missing and out of reach")
	void invisibleRecords() {
		signInAs(ALICE);
		Item own = new Item(ALICE, TenantContext.DEFAULT_TENANT);
		Item bobs = new Item(BOB, TenantContext.DEFAULT_TENANT);
		assertThat(inTenant(() -> service.openItem(Optional.of(own)))).isEqualTo(own);
		assertThatThrownBy(() -> inTenant(() -> service.openItem(Optional.of(bobs))))
			.isInstanceOfSatisfying(ApiException.class, (ex) -> assertThat(ex.code()).isEqualTo("probe.not_found"));
		assertThatThrownBy(() -> inTenant(() -> service.openItem(Optional.empty())))
			.isInstanceOfSatisfying(ApiException.class, (ex) -> assertThat(ex.code()).isEqualTo("probe.not_found"));
	}

	@Test
	@DisplayName("a refusal becomes exactly one decision record, with identifiers and no attribute values")
	void refusalIsRecorded(CapturedOutput output) {
		signInAs(ALICE);
		Item bobs = new Item(BOB, TenantContext.DEFAULT_TENANT);
		int before = decisionRecords(output).size();
		assertThatThrownBy(() -> inTenant(() -> service.update(bobs))).isInstanceOf(AccessDeniedException.class);
		List<String> records = decisionRecords(output);
		assertThat(records).hasSize(before + 1);
		String line = records.getLast();
		assertThat(line).startsWith("{")
			.contains(Reason.OUT_OF_REACH.code())
			.contains(UPDATE)
			.contains(ITEM)
			.contains(bobs.id().toString())
			.contains(ALICE.toString())
			.contains("deny");
	}

	@Test
	@DisplayName("an ordinary allow is counted but not recorded; an allow of a sensitive action is recorded with the system's reason")
	void allowsOfSensitiveActionsAreRecorded(CapturedOutput output) {
		signInAs(ALICE);
		double readsBefore = counted("allow", Reason.GRANTED.code(), READ);
		int before = decisionRecords(output).size();
		inTenant(service::read);
		assertThat(decisionRecords(output)).as("an ordinary allow leaves no record").hasSize(before);
		assertThat(counted("allow", Reason.GRANTED.code(), READ)).isEqualTo(readsBefore + 1);

		inTenant(() -> authorization.runAsSystem("test: purge expired items", service::purge));
		List<String> records = decisionRecords(output);
		assertThat(records).hasSize(before + 1);
		assertThat(records.getLast()).contains(PURGE).contains(Reason.SYSTEM.code()).contains("test: purge expired items");
	}

	@Test
	@DisplayName("a refused list leaves a record too")
	void refusedListIsRecorded(CapturedOutput output) {
		signInAs(ALICE);
		double before = counted("deny", Reason.NO_GRANT.code(), PURGE);
		assertThatThrownBy(() -> inTenant(service::filterOfPurges)).isInstanceOf(AccessDeniedException.class);
		assertThat(counted("deny", Reason.NO_GRANT.code(), PURGE)).isEqualTo(before + 1);
		assertThat(decisionRecords(output).getLast()).contains(PURGE).contains(Reason.NO_GRANT.code());
	}

}
