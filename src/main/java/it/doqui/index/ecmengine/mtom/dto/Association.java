package it.doqui.index.ecmengine.mtom.dto;

public class Association extends ContentItem {
    private static final long serialVersionUID = -2715830225080882278L;
    private boolean childAssociation;
    private String targetUid;
    private String targetPrefixedName;
    private String typePrefixedName;

    public Association() {
	super();
    }

    public Association(String prefixedName, boolean childAssociation, String targetUid, String targetPrefixedName,
	    String typePrefixedName) {
	super();
	this.setPrefixedName(prefixedName);
	this.childAssociation = childAssociation;
	this.targetUid = targetUid;
	this.targetPrefixedName = targetPrefixedName;
	this.typePrefixedName = typePrefixedName;
    }

    public Association(String prefixedName, boolean childAssociation, String typePrefixedName) {
	super();
	this.setPrefixedName(prefixedName);
	this.childAssociation = childAssociation;
	this.typePrefixedName = typePrefixedName;
    }

    public boolean isChildAssociation() {
	return childAssociation;
    }

    public void setChildAssociation(boolean childAssociation) {
	this.childAssociation = childAssociation;
    }

    public String getTargetUid() {
	return targetUid;
    }

    public void setTargetUid(String targetUid) {
	this.targetUid = targetUid;
    }

    public String getTargetPrefixedName() {
	return targetPrefixedName;
    }

    public void setTargetPrefixedName(String targetPrefixedName) {
	this.targetPrefixedName = targetPrefixedName;
    }

    public String getTypePrefixedName() {
	return typePrefixedName;
    }

    public void setTypePrefixedName(String typePrefixedName) {
	this.typePrefixedName = typePrefixedName;
    }

}
