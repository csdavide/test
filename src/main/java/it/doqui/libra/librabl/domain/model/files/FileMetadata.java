package it.doqui.libra.librabl.domain.model.files;

import it.doqui.libra.librabl.domain.model.document.SignData;

import java.util.List;

public interface FileMetadata extends FileDescriptor {
    String getName();
    String getContentUrl();
    String getMimetype();
    String getEncoding();
    String getLocale();
    String getFileName();
    boolean isOpaque();
    List<SignData> getSigns();
}
