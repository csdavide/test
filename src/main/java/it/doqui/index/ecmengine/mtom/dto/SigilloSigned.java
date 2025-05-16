package it.doqui.index.ecmengine.mtom.dto;

public class SigilloSigned extends MtomEngineDto {
    private static final long serialVersionUID = -6860987739849898344L;

    private String uid;

    public String getUid() {
	return uid;
    }

    public void setUid(String uid) {
	this.uid = uid;
    }

    private byte[] data;

    public byte[] getData() {
	return data;
    }

    public void setData(byte[] data) {
	this.data = data;
    }
}