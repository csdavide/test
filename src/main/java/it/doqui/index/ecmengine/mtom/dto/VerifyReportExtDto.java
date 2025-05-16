package it.doqui.index.ecmengine.mtom.dto;

public class VerifyReportExtDto extends MtomEngineDto {
    private static final long serialVersionUID = -4643027759478343351L;

    private String tokenUuid;

    public String getTokenUuid() {
	return tokenUuid;
    }

    public void setTokenUuid(String tokenUuid) {
	this.tokenUuid = tokenUuid;
    }

    private int signCount;

    public int getSignCount() {
	return signCount;
    }

    public void setSignCount(int signCount) {
	this.signCount = signCount;
    }

    private VerifyReport report;

    public VerifyReport getReport() {
	return report;
    }

    public void setReport(VerifyReport report) {
	this.report = report;
    }
}