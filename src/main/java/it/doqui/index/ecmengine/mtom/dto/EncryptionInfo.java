package it.doqui.index.ecmengine.mtom.dto;

public class EncryptionInfo extends MtomEngineDto {
    private static final long serialVersionUID = 3295205776179918050L;
    private String key;
    private String algorithm;
    private String padding;
    private String mode;
    private String keyId;
    private String sourceIV;
    private boolean sourceEncrypted;
    private boolean corruptedEncryptionInfo;

    public EncryptionInfo() {
	super();
    }

    public EncryptionInfo(String key, String algorithm, String padding, String mode, String keyId, String sourceIV,
	    boolean sourceEncrypted, boolean corruptedEncryptionInfo) {
	super();
	this.key = key;
	this.algorithm = algorithm;
	this.padding = padding;
	this.mode = mode;
	this.keyId = keyId;
	this.sourceIV = sourceIV;
	this.sourceEncrypted = sourceEncrypted;
	this.corruptedEncryptionInfo = corruptedEncryptionInfo;
    }

    public String getKey() {
	return key;
    }

    public void setKey(String key) {
	this.key = key;
    }

    public String getAlgorithm() {
	return algorithm;
    }

    public void setAlgorithm(String algorithm) {
	this.algorithm = algorithm;
    }

    public String getPadding() {
	return padding;
    }

    public void setPadding(String padding) {
	this.padding = padding;
    }

    public String getMode() {
	return mode;
    }

    public void setMode(String mode) {
	this.mode = mode;
    }

    public String getKeyId() {
	return keyId;
    }

    public void setKeyId(String keyId) {
	this.keyId = keyId;
    }

    public String getSourceIV() {
	return sourceIV;
    }

    public void setSourceIV(String sourceIV) {
	this.sourceIV = sourceIV;
    }

    public boolean isSourceEncrypted() {
	return sourceEncrypted;
    }

    public void setSourceEncrypted(boolean sourceEncrypted) {
	this.sourceEncrypted = sourceEncrypted;
    }

    public boolean isCorruptedEncryptionInfo() {
	return corruptedEncryptionInfo;
    }

    public void setCorruptedEncryptionInfo(boolean corruptedEncryptionInfo) {
	this.corruptedEncryptionInfo = corruptedEncryptionInfo;
    }
}
