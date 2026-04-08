package it.doqui.libra.librabl.domain.model.files;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.doqui.libra.librabl.utils.I18NUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Locale;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentBasicDescriptor implements ContentContainer {
    protected String name;

    @JsonProperty("mimetype") @JsonAlias("mimeType")
    protected String mimetype;
    protected String encoding;
    protected String locale;
    protected String fileName;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    protected boolean opaque;

    public Locale getLocale() {
        return I18NUtils.parseLocale(locale);
    }

    public void copyFrom(ContentBasicDescriptor d) {
        if (d != null) {
            this.name = d.name;
            this.mimetype = d.mimetype;
            this.encoding = d.encoding;
            this.locale = d.locale;
            this.fileName = d.fileName;
            this.opaque = d.opaque;
        }
    }

    public <T extends ContentBasicDescriptor> void mergeWith(T d) {
        if (d != null) {
            if (this.name == null) {
                this.name = d.name;
            }
            if (this.mimetype == null) {
                this.mimetype = d.mimetype;
            }
            if (this.encoding == null) {
                this.encoding = d.encoding;
            }
            if (this.locale == null) {
                this.locale = d.locale;
            }
            if (this.fileName == null) {
                this.fileName = d.fileName;
            }
        }
    }

    @JsonIgnore
    @Override
    public ContentBasicDescriptor getDescriptor() {
        return this;
    }
}
