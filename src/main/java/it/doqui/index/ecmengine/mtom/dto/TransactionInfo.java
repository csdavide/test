package it.doqui.index.ecmengine.mtom.dto;

public class TransactionInfo extends MtomEngineDto {
    private static final long serialVersionUID = 2463878318339708991L;

    private long transactionId;
    private long nodeId;
    private String uid;

    public long getTransactionId() {
	return transactionId;
    }

    public void setTransactionId(long transactionId) {
	this.transactionId = transactionId;
    }

    public long getNodeId() {
	return nodeId;
    }

    public void setNodeId(long nodeId) {
	this.nodeId = nodeId;
    }

    public String getUid() {
	return uid;
    }

    public void setUid(String uid) {
	this.uid = uid;
    }
}