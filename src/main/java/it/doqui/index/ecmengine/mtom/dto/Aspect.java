package it.doqui.index.ecmengine.mtom.dto;

public class Aspect extends ItemsContainer {
    private static final long serialVersionUID = -179316880631742715L;

    private String modelPrefixedName;

    public Aspect() {
	super();
    }

    public Aspect(String prefixedName, String modelPrefixedName) {
	super();
	this.setPrefixedName(prefixedName);
	this.setModelPrefixedName(modelPrefixedName);
    }

    public String getModelPrefixedName() {
	return modelPrefixedName;
    }

    public void setModelPrefixedName(String modelPrefixedName) {
	this.modelPrefixedName = modelPrefixedName;
    }

}
