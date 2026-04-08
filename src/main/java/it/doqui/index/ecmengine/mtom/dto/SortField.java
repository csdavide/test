package it.doqui.index.ecmengine.mtom.dto;

import java.io.Serial;
import java.io.Serializable;

public class SortField implements Serializable {

    @Serial
    private static final long serialVersionUID = -6824769815720267423L;

    private String fieldName;
    private boolean ascending;

    public SortField() {
	super();
    }

    public SortField(String fieldName, boolean ascending) {
	super();
	this.fieldName = fieldName;
	this.ascending = ascending;
    }

    public String getFieldName() {
	return fieldName;
    }

    public void setFieldName(String fieldName) {
	this.fieldName = fieldName;
    }

    public boolean isAscending() {
	return ascending;
    }

    public void setAscending(boolean ascending) {
	this.ascending = ascending;
    }
}
