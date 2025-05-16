package it.doqui.libra.librabl.utils;

import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class IOUtils {

    private IOUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static byte[] readFully(InputStream is) throws IOException {
        if (is == null) {
            return new byte[0];
        }

        try (is; var bos = new ByteArrayOutputStream()) {
            var buffer = new byte[1024];
            int n;
            while ((n = is.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
            }

            return bos.toByteArray();
        }
    }

    public static String getFileName(String contentDisposition) {
        String fileName = null;
        if (contentDisposition != null) {
            int p0 = contentDisposition.indexOf("filename=");
            if (p0 >= 0 && p0 + 9 < contentDisposition.length()) {
                if (contentDisposition.charAt(p0 + 9) == '\"') {
                    int p1 = contentDisposition.indexOf('\"', p0 + 10);
                    if (p1 >= 0) {
                        fileName = contentDisposition.substring(p0 + 10, p1);
                    }
                } else {
                    fileName = contentDisposition.substring(p0 + 9);
                }
            }
        }

        return fileName;
    }

    public static String mimeType(String mimeType) {
        try {
            var m = MediaType.valueOf(mimeType);
            var charset = m.getParameters().get("charset");
            mimeType = m.getType() + "/" + m.getSubtype() + (StringUtils.isNotBlank(charset) ? ";charset=" + charset : "");
        } catch (Exception e) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return mimeType;
    }
}
