package it.doqui.libra.librabl.views.share;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeyRequest {

    private String publicKey;

    /**
     * @deprecated repository in kept only for index compatibility. Libra does not support and ignore repository
     */
    @Deprecated
    private String repository;

    @JsonProperty("tenant")
    @JsonAlias("tenantName")
    private String tenant;

    @JsonProperty("uuid")
    @JsonAlias("uid")
    private String uuid;

    @JsonProperty("contentPropertyName")
    @JsonAlias("contentPropertyPrefixedName")
    private String contentPropertyName;

    @JsonProperty("validUntil")
    private Long validUntil;
}
