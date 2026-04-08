package it.doqui.libra.librabl.domain.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class SignData {
    private String authority;
    private String identity;
    private ZonedDateTime signedAt;
    private String username;
    private SignType signType;
    private Provider provider;
    private DocumentOperation documentOperation;
}
