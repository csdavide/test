package it.doqui.libra.librabl.api.v1.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tenant {

    private String name;
    private String password;

    @JsonProperty("name")
    public String getName() {
	return name;
    }

    public void setName(String name) {
	this.name = name;
    }

    @JsonProperty("password")
    public String getPassword() {
	return password;
    }

    public void setPassword(String password) {
	this.password = password;
    }

    @Override
    public boolean equals(Object o) {
	if (this == o) {
	    return true;
	}
	if (o == null || getClass() != o.getClass()) {
	    return false;
	}
	Tenant oth = (Tenant) o;
	return Objects.equals(name, oth.name) && Objects.equals(password, oth.password);
    }

    @Override
    public int hashCode() {
	return Objects.hash(name, password);
    }

    @Override
    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append("class Tenant {\n");
	sb.append("    name: ").append(toIndentedString(name)).append("\n");
	sb.append("    password: ").append(toIndentedString(password)).append("\n");
	sb.append("}");
	return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
	if (o == null) {
	    return "null";
	}
	return o.toString().replace("\n", "\n    ");
    }
}