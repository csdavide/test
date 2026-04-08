package it.doqui.index.ecmengine.mtom.dto;

public class KeyRequestDto {
    private String publicKey;
    private String repository;
    private String tenantName;
    private String uid;
    private String contentPropertyPrefixedName;
    private Long validUntil;

    public String getPublicKey() {
	return publicKey;
    }

    public void setPublicKey(String publicKey) {
	this.publicKey = publicKey;
    }

    public String getTenantName() {
	return tenantName;
    }

    public void setTenantName(String tenantName) {
	this.tenantName = tenantName;
    }

    public String getContentPropertyPrefixedName() {
	return contentPropertyPrefixedName;
    }

    public void setContentPropertyPrefixedName(String contentPropertyPrefixedName) {
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
    }

    public String getUid() {
	return uid;
    }

    public void setUid(String uid) {
	this.uid = uid;
    }

    public Long getValidUntil() {
	return validUntil;
    }

    public void setValidUntil(Long validUntil) {
	this.validUntil = validUntil;
    }

    public String getRepository() {
	return repository;
    }

    public void setRepository(String repository) {
	this.repository = repository;
    }

}
