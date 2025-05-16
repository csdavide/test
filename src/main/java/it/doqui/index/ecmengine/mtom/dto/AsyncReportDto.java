package it.doqui.index.ecmengine.mtom.dto;

public class AsyncReportDto extends MtomEngineDto {
    private static final long serialVersionUID = 8060998271737667470L;

    public static Status ERROR = Status.ERROR;
    public static Status EXPIRED = Status.EXPIRED;
    public static Status READY = Status.READY;
    public static Status SCHEDULED = Status.SCHEDULED;

    private VerifyReport report;

    public VerifyReport getReport() {
	return report;
    }

    public void setReport(VerifyReport report) {
	this.report = report;
    }

    private Status status;

    public Status getStatus() {
	return status;
    }

    public void setStatus(Status status) {
	this.status = status;
    }
}