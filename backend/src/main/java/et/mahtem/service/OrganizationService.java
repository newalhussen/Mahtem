package et.mahtem.service;

import et.mahtem.domain.AppUser;
import et.mahtem.domain.Organization;
import et.mahtem.domain.Role;
import et.mahtem.domain.Signatory;
import et.mahtem.domain.SigningKey;
import et.mahtem.domain.UserStatus;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.ConsoleDtos.KeyRow;
import et.mahtem.web.dto.ConsoleDtos.Me;
import et.mahtem.web.dto.ConsoleDtos.Member;
import et.mahtem.web.dto.ConsoleDtos.OrgProfile;
import et.mahtem.web.dto.ConsoleDtos.OrgSummary;
import et.mahtem.web.dto.ConsoleDtos.TemplateRow;
import et.mahtem.web.dto.OrgDtos.SignatoryInput;
import et.mahtem.web.dto.OrgDtos.UpdateMemberRequest;
import et.mahtem.web.dto.OrgDtos.UpdateProfileRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization profile, domain verification, signing keys, team members and templates. */
@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final CredentialRepository credentials;
    private final KeyService keys;
    private final AuditService audit;

    public OrganizationService(OrganizationRepository organizations, AppUserRepository users, CredentialRepository credentials,
                               KeyService keys, AuditService audit) {
        this.organizations = organizations;
        this.users = users;
        this.credentials = credentials;
        this.keys = keys;
        this.audit = audit;
    }

    /* ---------------------------------------------------------------------------------- session */

    @Transactional
    public Me me(Actor actor) {
        AppUser u = users.findById(actor.userId()).orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
        Organization org = organization(actor);
        SigningKey key = keys.activeKey(org);
        return new Me(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isTotpEnabled(),
                new OrgSummary(org.getCode(), org.getName(), org.getInitials(), org.getDepartment(), org.getDomain(), org.isDomainVerified(),
                        KeyService.shortFingerprint(key.getFingerprint()), key.getLabel(), key.getValidTo(),
                        u.getRole().canIssue(), u.getRole().canApprove()));
    }

    /* ---------------------------------------------------------------------------------- profile */

    @Transactional(readOnly = true)
    public OrgProfile profile(Actor actor) {
        Organization o = organization(actor);
        return new OrgProfile(o.getCode(), o.getName(), o.getNameAm(), o.getInitials(), o.getDepartment(), o.getAccreditationBody(),
                o.getCountry(), o.getVerifierContact(), o.getDomain(), o.isDomainVerified(), o.getDomainVerifiedAt(),
                txtName(o), txtValue(o), o.isShowDetailsPublicly(), o.isShowRevocationReason(), o.isAllowPdfDownload(), o.getSignatories());
    }

    @Transactional
    public OrgProfile updateProfile(Actor actor, UpdateProfileRequest r) {
        actor.require(Role.ADMIN);
        Organization o = organization(actor);
        o.setName(r.name().trim());
        o.setNameAm(blank(r.nameAm()));
        o.setInitials(r.initials().trim().toUpperCase());
        o.setDepartment(blank(r.department()));
        o.setAccreditationBody(blank(r.accreditationBody()));
        o.setCountry(r.country().trim());
        o.setVerifierContact(blank(r.verifierContact()));
        o.setShowDetailsPublicly(r.showDetailsPublicly());
        o.setShowRevocationReason(r.showRevocationReason());
        o.setAllowPdfDownload(r.allowPdfDownload());
        if (r.signatories() != null) {
            o.setSignatories(r.signatories().stream().map((SignatoryInput s) -> new Signatory(s.name().trim(), s.title().trim())).toList());
        }
        organizations.save(o);
        audit.record(actor, "Settings changed", "Organization profile and public page options");
        return profile(actor);
    }

    /** Looks for the TXT record that proves the organization controls its domain. */
    @Transactional
    public OrgProfile verifyDomain(Actor actor) {
        actor.require(Role.ADMIN);
        Organization o = organization(actor);
        if (!o.isDomainVerified()) {
            if (!hasTxt(txtName(o), txtValue(o))) {
                throw ApiException.conflict("DNS_NOT_FOUND", "We could not find the TXT record yet. DNS changes can take a few minutes to appear.");
            }
            o.markDomainVerified(Instant.now());
            organizations.save(o);
            audit.record(actor, "Domain verified", o.getDomain());
        }
        return profile(actor);
    }

    static String txtName(Organization o) {
        return "_mahtem." + o.getDomain();
    }

    static String txtValue(Organization o) {
        return "mahtem-site-verification=" + o.getDomainToken();
    }

    private boolean hasTxt(String name, String expected) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            Attributes attrs = new InitialDirContext(env).getAttributes(name, new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");
            if (txt == null) return false;
            NamingEnumeration<?> all = txt.getAll();
            while (all.hasMore()) {
                if (String.valueOf(all.next()).replace("\"", "").trim().equals(expected)) return true;
            }
            return false;
        } catch (NamingException e) {
            return false;
        }
    }

    /* ---------------------------------------------------------------------------------- keys */

    @Transactional(readOnly = true)
    public List<KeyRow> keys(Actor actor) {
        return keys.list(actor.organizationId()).stream()
                .map(k -> new KeyRow(k.getId(), k.getLabel(), KeyService.shortFingerprint(k.getFingerprint()), k.getAlgorithm(), k.getValidFrom(), k.getValidTo(), k.getStatus().name()))
                .toList();
    }

    @Transactional
    public List<KeyRow> rotateKey(Actor actor) {
        actor.require(Role.ADMIN);
        SigningKey fresh = keys.rotate(organization(actor));
        audit.record(actor, "Key rotated", fresh.getLabel() + " activated");
        return keys(actor);
    }

    /* ---------------------------------------------------------------------------------- team */

    @Transactional(readOnly = true)
    public List<Member> team(Actor actor) {
        return users.findByOrganizationIdOrderByFullNameAsc(actor.organizationId()).stream().map(this::member).toList();
    }

    @Transactional
    public Member updateMember(Actor actor, UUID memberId, UpdateMemberRequest r) {
        actor.require(Role.ADMIN);
        AppUser m = users.findByIdAndOrganizationId(memberId, actor.organizationId()).orElseThrow(() -> ApiException.notFound("Team member"));
        boolean demotes = r.role() != null && r.role() != Role.ADMIN && m.getRole() == Role.ADMIN;
        boolean disables = Boolean.TRUE.equals(r.disabled()) && m.getRole() == Role.ADMIN;
        if ((demotes || disables) && admins(actor.organizationId()) <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "An organization needs at least one active Admin.");
        }
        if (m.getId().equals(actor.userId()) && (r.role() != null && r.role() != m.getRole() || Boolean.TRUE.equals(r.disabled()))) {
            throw ApiException.conflict("SELF_CHANGE", "Ask another Admin to change your own access.");
        }
        if (r.role() != null) m.setRole(r.role());
        if (r.disabled() != null) {
            if (r.disabled()) m.setStatus(UserStatus.DISABLED);
            else if (m.getStatus() == UserStatus.DISABLED) m.setStatus(m.getPasswordHash() == null ? UserStatus.INVITED : UserStatus.ACTIVE);
        }
        users.save(m);
        audit.record(actor, "Member changed", m.getEmail() + " · " + m.getRole().name().toLowerCase() + " · " + m.getStatus().name().toLowerCase());
        return member(m);
    }

    private long admins(UUID orgId) {
        return users.findByOrganizationIdOrderByFullNameAsc(orgId).stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE).count();
    }

    private Member member(AppUser u) {
        return new Member(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus().name(), u.isTotpEnabled(), u.getLastLoginAt());
    }

    /* ---------------------------------------------------------------------------------- templates */

    @Transactional(readOnly = true)
    public List<TemplateRow> templates(Actor actor) {
        Map<et.mahtem.domain.CredentialType, Long> used = new java.util.EnumMap<>(et.mahtem.domain.CredentialType.class);
        for (Object[] row : credentials.countSealedByType(actor.organizationId())) used.put((et.mahtem.domain.CredentialType) row[0], (Long) row[1]);
        return Arrays.stream(et.mahtem.domain.CredentialType.values())
                .map(t -> new TemplateRow(t, t.label(), used.getOrDefault(t, 0L))).toList();
    }

    /* ---------------------------------------------------------------------------------- helpers */

    private Organization organization(Actor actor) {
        return organizations.findById(actor.organizationId()).orElseThrow(() -> ApiException.notFound("Organization"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
