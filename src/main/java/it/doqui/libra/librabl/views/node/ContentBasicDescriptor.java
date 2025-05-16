package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    protected String mimetype;
    protected String encoding;
    protected String locale;
    protected String fileName;
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

    @JsonIgnore
    @Override
    public ContentBasicDescriptor getDescriptor() {
        return this;
    }
}
