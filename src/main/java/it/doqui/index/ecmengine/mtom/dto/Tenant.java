package it.doqui.index.ecmengine.mtom.dto;

public class Tenant extends MtomEngineDto {
    private static final long serialVersionUID = -5195391235934001253L;

    private String domain;
    private String rootContentStoreDir;
    private String adminPassword;
    private boolean enabled;

    private ContentStoreDefinition[] contentStore;

    public String getDomain() {
	return domain;
    }

    public void setDomain(String domain) {
	this.domain = domain;
    }

    public String getRootContentStoreDir() {
	return rootContentStoreDir;
    }

    public void setRootContentStoreDir(String rootContentStoreDir) {
	this.rootContentStoreDir = rootContentStoreDir;
    }

    public String getAdminPassword() {
	return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
	this.adminPassword = adminPassword;
    }

    public ContentStoreDefinition[] getContentStore() {
	return contentStore;
    }

    public void setContentStore(ContentStoreDefinition[] contentStore) {
	this.contentStore = contentStore;
    }

    @Deprecated
    public boolean isEnabled() {
        return enabled;
    }

    @Deprecated
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}