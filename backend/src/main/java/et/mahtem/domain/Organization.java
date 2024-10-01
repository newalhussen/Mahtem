package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An issuing institution (university, training body, employer, licensing authority…). */
@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Column(nullable = false)
    private String name;

    private String nameAm;

    @Column(nullable = false)
    private String initials;

    private String department;
    private String accreditationBody;

    @Column(nullable = false)
    private String country = "Ethiopia";

    private String verifierContact;

    @Column(nullable = false)
    private String domain;

    private boolean domainVerified;
    private Instant domainVerifiedAt;

    @Column(nullable = false)
    private String domainToken;

    @Convert(converter = SignatoryListConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<Signatory> signatories = new ArrayList<>();

    private boolean showDetailsPublicly = true;
    private boolean showRevocationReason = true;
    private boolean allowPdfDownload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Organization() {}

    public Organization(String code, String name, String initials, String domain, String domainToken) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.initials = initials;
        this.domain = domain;
        this.domainToken = domainToken;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameAm() { return nameAm; }
    public void setNameAm(String nameAm) { this.nameAm = nameAm; }
    public String getInitials() { return initials; }
    public void setInitials(String initials) { this.initials = initials; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getAccreditationBody() { return accreditationBody; }
    public void setAccreditationBody(String v) { this.accreditationBody = v; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getVerifierContact() { return verifierContact; }
    public void setVerifierContact(String v) { this.verifierContact = v; }
    public String getDomain() { return domain; }
    public boolean isDomainVerified() { return domainVerified; }
    public Instant getDomainVerifiedAt() { return domainVerifiedAt; }
    public String getDomainToken() { return domainToken; }
    public List<Signatory> getSignatories() { return signatories; }
    public void setSignatories(List<Signatory> signatories) { this.signatories = new ArrayList<>(signatories); }
    public boolean isShowDetailsPublicly() { return showDetailsPublicly; }
    public void setShowDetailsPublicly(boolean v) { this.showDetailsPublicly = v; }
    public boolean isShowRevocationReason() { return showRevocationReason; }
    public void setShowRevocationReason(boolean v) { this.showRevocationReason = v; }
    public boolean isAllowPdfDownload() { return allowPdfDownload; }
    public void setAllowPdfDownload(boolean v) { this.allowPdfDownload = v; }
    public Instant getCreatedAt() { return createdAt; }

    public void markDomainVerified(Instant at) {
        this.domainVerified = true;
        this.domainVerifiedAt = at;
    }
}
