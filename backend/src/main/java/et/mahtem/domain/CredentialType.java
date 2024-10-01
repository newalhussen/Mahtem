package et.mahtem.domain;

public enum CredentialType {
    DEGREE("Degree", "Conferred", "having fulfilled all requirements prescribed by the Academic Senate, has been awarded the degree of"),
    DIPLOMA("Diploma", "Conferred", "having completed the prescribed programme of study, is awarded the diploma in"),
    TRAINING("Training certificate", "Completed", "having successfully completed the prescribed course of study and assessment, is awarded the"),
    CERTIFICATION("Professional certification", "Certified", "having demonstrated the knowledge and skills required, is certified as"),
    EMPLOYMENT("Employment credential", "Start date", "is confirmed to be employed by this organization in the position of"),
    AWARD("Award", "Awarded", "is recognised with the"),
    LICENSE("License", "Licensed", "is licensed and authorised to practise as");

    private final String label;
    private final String dateLabel;
    private final String defaultStatement;

    CredentialType(String label, String dateLabel, String defaultStatement) {
        this.label = label;
        this.dateLabel = dateLabel;
        this.defaultStatement = defaultStatement;
    }

    public String label() { return label; }
    public String dateLabel() { return dateLabel; }
    public String defaultStatement() { return defaultStatement; }
}
