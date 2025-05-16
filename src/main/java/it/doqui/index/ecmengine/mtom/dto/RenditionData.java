package it.doqui.index.ecmengine.mtom.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class RenditionData extends MtomEngineDto {
    private static final long serialVersionUID = 7932530058128646404L;
    String nodeId;
    String contentPropertyPrefixedName;
    String typePrefixedName;
    String parentAssocTypePrefixedName;
    String prefixedName;
    String mimeType;
    String encoding;

    //@JsonIgnore
    byte[] content;

    // Parete rendition transformer
    String rtNodeId;
    String genMymeType;
    String rtContentPropertyPrefixedName;

    public String getNodeId() {
	return nodeId;
    }

    public void setNodeId(String nodeId) {
	this.nodeId = nodeId;
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

    public String getParentAssocTypePrefixedName() {
	return parentAssocTypePrefixedName;
    }

    public void setParentAssocTypePrefixedName(String parentAssocTypePrefixedName) {
	this.parentAssocTypePrefixedName = parentAssocTypePrefixedName;
    }

    public String getPrefixedName() {
	return prefixedName;
    }

    public void setPrefixedName(String prefixedName) {
	this.prefixedName = prefixedName;
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

    public String getRtNodeId() {
	return rtNodeId;
    }

    public void setRtNodeId(String rtNodeId) {
	this.rtNodeId = rtNodeId;
    }

    public String getGenMymeType() {
	return genMymeType;
    }

    public void setGenMymeType(String genMymeType) {
	this.genMymeType = genMymeType;
    }

    public String getRtContentPropertyPrefixedName() {
	return rtContentPropertyPrefixedName;
    }

    public void setRtContentPropertyPrefixedName(String rtContentPropertyPrefixedName) {
	this.rtContentPropertyPrefixedName = rtContentPropertyPrefixedName;
    }

    public byte[] getContent() {
	return content;
    }

    public void setContent(byte[] content) {
	this.content = content;
    }

}
