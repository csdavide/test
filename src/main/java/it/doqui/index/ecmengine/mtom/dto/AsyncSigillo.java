package it.doqui.index.ecmengine.mtom.dto;

public class AsyncSigillo extends MtomEngineDto {
    private static final long serialVersionUID = 4495019007884266309L;

    public static Status ERROR = Status.ERROR;
    public static Status EXPIRED = Status.EXPIRED;
    public static Status READY = Status.READY;
    public static Status SCHEDULED = Status.SCHEDULED;

    //@JsonIgnore
    private byte[] signed;

    //@JsonIgnore
    public byte[] getSigned() {
	return signed;
    }

    public void setSigned(byte[] signed) {
	this.signed = signed;
    }

    private Status status;

    public Status getStatus() {
	return status;
    }

    public void setStatus(Status status) {
	this.status = status;
    }
}