package it.doqui.index.ecmengine.mtom.dto;

public class ContentStoreDefinition extends MtomEngineDto {
    private static final long serialVersionUID = -7170491396552438703L;
    private String type;
    private String protocol;
    private String resource;

    public String getType() {
	return type;
    }

    public void setType(String type) {
	this.type = type;
    }

    public String getProtocol() {
	return protocol;
    }

    public void setProtocol(String protocol) {
	this.protocol = protocol;
    }

    public String getResource() {
	return resource;
    }

    public void setResource(String resource) {
	this.resource = resource;
    }
}
