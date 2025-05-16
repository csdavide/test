package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.jetty.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorMessage {
    private final int status;
    private final String code;

    @JsonProperty("title")
    private final String message;

    @JsonIgnore
    private final Map<String, String> detailMap;

    @JsonProperty("links")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<String> tags;

    public ErrorMessage(int status, String message) {
        this.status = status;
        this.code = HttpStatus.getMessage(status);
        this.message = message;
        this.detailMap = new LinkedHashMap<>();
        this.tags = new ArrayList<>();
    }

    @JsonProperty("detail")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ErrorDetail> getDetails() {
        return detailMap.entrySet()
            .stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> new ErrorDetail(entry.getKey(), entry.getValue()))
            .toList();
    }

    @Getter
    @Setter
    public static class ErrorDetail {
        private final String key;
        private final String value;

        public ErrorDetail(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
