package it.doqui.index.ecmengine.mtom.dto;

public class Content extends ItemsContainer {
    private static final long serialVersionUID = -2914919167121337995L;
    private String uid;
    private String contentPropertyPrefixedName;
    private String typePrefixedName;
    private String mimeType;
    private String encoding;
    private long size;
    private Aspect[] aspects;
    private EncryptionInfo encryptionInfo;
    private String modelPrefixedName;
    private String parentAssocTypePrefixedName;
    private Property[] properties;
    private Association[] associations;

    //@JsonIgnore
    private byte[] content;
    private boolean optimize;

    public Content() {
	super();
	this.setAspects(new Aspect[0]);
    }

    public Content(String uid, String contentPropertyPrefixedName, String typePrefixedName, String mimeType,
	    String encoding, long size, Aspect[] aspects, EncryptionInfo encryptionInfo, String modelPrefixedName,
	    String parentAssocTypePrefixedName, Property[] properties, Association[] associations, String prefixedName,
	    byte[] content, boolean optimize) {
	super();
	this.uid = uid;
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
	this.typePrefixedName = typePrefixedName;
	this.mimeType = mimeType;
	this.encoding = encoding;
	this.size = size;
	this.setAspects(aspects);
	this.encryptionInfo = encryptionInfo;
	this.content = content;
	this.optimize = optimize;
	this.modelPrefixedName = modelPrefixedName;
	this.parentAssocTypePrefixedName = parentAssocTypePrefixedName;
	this.properties = properties;
	this.associations = associations;
	this.setPrefixedName(prefixedName);
    }

    public Content(String contentPropertyPrefixedName, String typePrefixedName, String mimeType, String encoding,
	    Aspect[] aspects, EncryptionInfo encryptionInfo, String modelPrefixedName,
	    String parentAssocTypePrefixedName, Property[] properties, String prefixedName, byte[] content,
	    boolean optimize) {
	super();
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
	this.typePrefixedName = typePrefixedName;
	this.mimeType = mimeType;
	this.encoding = encoding;
	this.setAspects(aspects);
	this.encryptionInfo = encryptionInfo;
	this.content = content;
	this.optimize = optimize;
	this.modelPrefixedName = modelPrefixedName;
	this.parentAssocTypePrefixedName = parentAssocTypePrefixedName;
	this.properties = properties;
	this.setPrefixedName(prefixedName);
    }

    public String getUid() {
	return uid;
    }

    public void setUid(String uid) {
	this.uid = uid;
    }

    public String getContentPropertyPrefixedName() {
	return contentPropertyPrefixedName;
    }

    public void setContentPropertyPrefixedName(String contentPropertyPrefixedName) {
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
    }

    public String getTypePrefixedName() {
	return typePrefixedName;
    }

    public void setTypePrefixedName(String typePrefixedName) {
	this.typePrefixedName = typePrefixedName;
    }

    public String getMimeType() {
	return mimeType;
    }

    public void setMimeType(String mimeType) {
	this.mimeType = mimeType;
    }

    public String getEncoding() {
	return encoding;
    }

    public void setEncoding(String encoding) {
	this.encoding = encoding;
    }

    public long getSize() {
	return size;
    }

    public void setSize(long size) {
	this.size = size;
    }

    public Aspect[] getAspects() {
	return aspects;
    }

    public void setAspects(Aspect[] aspects) {
	// L'array degli aspects non deve essere nullo (al limite vuoto).
	this.aspects = aspects;
	if (this.aspects == null)
	    this.aspects = new Aspect[0];
    }

    public EncryptionInfo getEncryptionInfo() {
	return encryptionInfo;
    }

    public void setEncryptionInfo(EncryptionInfo encryptionInfo) {
	this.encryptionInfo = encryptionInfo;
    }

    public String getModelPrefixedName() {
	return modelPrefixedName;
    }

    public void setModelPrefixedName(String modelPrefixedName) {
	this.modelPrefixedName = modelPrefixedName;
    }

    public String getParentAssocTypePrefixedName() {
	return parentAssocTypePrefixedName;
    }

    public void setParentAssocTypePrefixedName(String parentAssocTypePrefixedName) {
	this.parentAssocTypePrefixedName = parentAssocTypePrefixedName;
    }

    public Property[] getProperties() {
	return properties;
    }

    public void setProperties(Property[] properties) {
	this.properties = properties;
    }

    public Association[] getAssociations() {
	return associations;
    }

    public void setAssociations(Association[] associations) {
	this.associations = associations;
    }

    public byte[] getContent() {
	return content;
    }

    public void setContent(byte[] content) {
	this.content = content;
    }

    public boolean isOptimize() {
	return optimize;
    }

    public void setOptimize(boolean optimize) {
	this.optimize = optimize;
    }
}
