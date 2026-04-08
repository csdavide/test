package it.doqui.index.ecmengine.mtom.dto;

public class SigilloSignedExt extends MtomEngineDto {
    private static final long serialVersionUID = -7920038287639617760L;

    private String tokenUid;

    public String getTokenUid() {
	return tokenUid;
    }

    public void setTokenUid(String tokenUid) {
	this.tokenUid = tokenUid;
    }

    private SigilloSigned signed;

    public SigilloSigned getSigned() {
	return signed;
    }

    public void setSigned(SigilloSigned signed) {
	this.signed = signed;
    }
}