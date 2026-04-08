package it.doqui.libra.librabl.domain.model.files;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Path;

@Getter
@Builder
public class NodeAttachment implements ContentContainer {
    private String name;
    private ContentProperty contentProperty;
    private File file;
    private String disposition;
    private boolean opaque;

    @JsonIgnore
    private StoreLocation store;

    public String formatDisposition(boolean inline) {
        if (StringUtils.isBlank(disposition)) {
            return String.format("%s; filename=\"%s\"", inline ? "inline" : "attachment", name);
        }

        return disposition
            .replace("#{filename}", name)
            .replace("#{mode}", inline ? "inline" : "attachment");
    }

    @Override
    public ContentBasicDescriptor getDescriptor() {
        return contentProperty;
    }

    @Getter
    @Builder
    public static class StoreLocation {
        private String dbSchema;
        private String tenant;
        private Path path;
    }
}
