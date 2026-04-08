package it.doqui.index.ecmengine.mtom.dto;

public class Property extends ContentItem {
    private static final long serialVersionUID = -2045302444337474899L;
    private String dataType;
    private boolean multivalue;
    private String[] values;
    private String relativeAspectPrefixedName;

    public String getDataType() {
	return dataType;
    }

    public void setDataType(String dataType) {
	this.dataType = dataType;
    }

    public String getRelativeAspectPrefixedName() {
	return relativeAspectPrefixedName;
    }

    public void setRelativeAspectPrefixedName(String relativeAspectPrefixedName) {
	this.relativeAspectPrefixedName = relativeAspectPrefixedName;
    }

    public Property() {
	super();
    }

    public Property(String prefixedName, boolean multivalue, String[] values) {
	super();
	this.setPrefixedName(prefixedName);
	this.multivalue = multivalue;
	this.values = values;
    }

    public boolean isMultivalue() {
	return multivalue;
    }

    public void setMultivalue(boolean multivalue) {
	this.multivalue = multivalue;
    }

    public String[] getValues() {
	return values;
    }

    public void setValues(String[] values) {
	this.values = values;
    }

    @Deprecated
    public String getValue() {
	return (this.values != null && this.values.length > 0) ? this.values[0] : null;
    }

}
