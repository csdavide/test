package it.doqui.libra.librabl.domain.service;

import it.doqui.libra.librabl.domain.ports.out.MimeTypeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Set;

@ApplicationScoped
public class MimeTypeService {

    @Inject
    MimeTypeRepository mimeTypeRepository;

    public String filename(String fileName, String mimeType) {
        if (StringUtils.isNotBlank(mimeType) && fileName != null && !mimeType.equals("application/octet-stream")) {
            var extensions = mimeTypeRepository.getAllFileExtensions(mimeType.toLowerCase(), false);
            if (extensions != null && !extensions.isEmpty()) {
                var name = fileName.toLowerCase();
                if (extensions.stream().map(String::toLowerCase).noneMatch(ext -> name.endsWith("." + ext))) {
                    fileName = fileName + "." + extensions.get(0);
                }
            }
        }

        return fileName;
    }

    public String filename(String fileName, String mimeType, Set<String> nameSet) {
        if (fileName != null) {
            var prefix = fileName;
            var lastDot = fileName.lastIndexOf('.');
            final String ext;
            if (lastDot > 0) {
                prefix = fileName.substring(0, lastDot);
                ext = fileName.substring(lastDot).toLowerCase();
            } else {
                ext = "";
            }

            String suffix;
            if (StringUtils.isNotBlank(ext)) {
                suffix = ext;
            } else if (mimeType != null) {
                suffix = mimeTypeRepository.getFileExtension(mimeType, false).map(x -> "." + x).orElse("");
            } else {
                suffix = "";
            }

            if (StringUtils.isBlank(suffix)) {
                suffix = ext;
            } else if (!Strings.CI.equals(ext, suffix)) {
                prefix += ext;
            }

            fileName = prefix + suffix;
            var count = 0;
            while (nameSet.contains(fileName)) {
                count++;
                fileName = prefix + "_" + count + suffix;
            }
        }

        return filename(fileName, mimeType);
    }

    public String getMimeType(String fileName) {
        return mimeTypeRepository.getAllMimeTypes(fileName).stream().findFirst().orElse(null);
    }

}
