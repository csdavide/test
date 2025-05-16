package it.doqui.index.ecmengine.mtom.dto;

public class KeyPayloadDto {
    private KeyRequestDto request;

    private String signature;

    public KeyRequestDto getRequest() {
	return request;
    }

    public void setRequest(KeyRequestDto request) {
	this.request = request;
    }

    public String getSignature() {
	return signature;
    }

    public void setSignature(String signature) {
	this.signature = signature;
    }
}
