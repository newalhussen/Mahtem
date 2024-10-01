package et.mahtem.domain;

public enum Role {
    ADMIN, APPROVER, ISSUER, VIEWER;

    /** Can create, seal and revoke credentials. */
    public boolean canIssue() { return this == ADMIN || this == ISSUER; }

    /** Can approve (seal) credentials that were created by someone else. */
    public boolean canApprove() { return this == ADMIN || this == APPROVER; }
}
