package et.mahtem.domain;

/** A person whose name and title are printed (as a signature) on an organization's certificates. */
public record Signatory(String name, String title) {}
