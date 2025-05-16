package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReindexParameters {

    private String hostName;
    private String command;
    private String[] uuids;
    private String[] ids;
    private boolean updateMaps;

    @JsonProperty("hostName")
    public String getHostName() {
	return hostName;
    }

    public void setHostName(String hostName) {
	this.hostName = hostName;
    }

    @JsonProperty("command")
    public String getCommand() {
	return command;
    }

    public void setCommand(String command) {
	this.command = command;
    }

    @JsonProperty("uuids")   
    public String[] getUuids() {
	return uuids;
    }
    
    public void setUuids(String[] uuids) {
	this.uuids = uuids;
    }
    
    @JsonProperty("ids")   
    public String[] getIds() {
	return ids;
    }
    
    public void setIds(String[] ids) {
	this.ids = ids;
    }    

    @JsonProperty("updateMaps")
    public boolean isUpdateMaps() {
	return updateMaps;
    }

    public void setUpdateMaps(boolean updateMaps) {
	this.updateMaps = updateMaps;
    }

    @Override
    public boolean equals(Object o) {
	if (this == o) {
	    return true;
	}
	if (o == null || getClass() != o.getClass()) {
	    return false;
	}
	ReindexParameters oth = (ReindexParameters) o;
	return Objects.equals(hostName, oth.hostName) 
		&& Objects.equals(command, oth.command)
		&& Objects.equals(uuids, oth.uuids) 
		&& Objects.equals(ids, oth.ids) 
		&& Objects.equals(updateMaps, oth.updateMaps);
    }

    @Override
    public int hashCode() {
	return Objects.hash(hostName, command, uuids, updateMaps);
    }

    @Override
    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append("class ReindexParameters {\n");
	sb.append("    hostName: ").append(toIndentedString(hostName)).append("\n");
	sb.append("    command: ").append(toIndentedString(command)).append("\n");
	sb.append("    uuids: ").append(toIndentedString(uuids)).append("\n");
	sb.append("    ids: ").append(toIndentedString(ids)).append("\n");
	sb.append("    updateMaps: ").append(toIndentedString(updateMaps)).append("\n");
	sb.append("}");
	return sb.toString();
    }

    private String toIndentedString(Object o) {
	if (o == null) {
	    return "null";
	}
	return o.toString().replace("\n", "\n    ");
    }
}
