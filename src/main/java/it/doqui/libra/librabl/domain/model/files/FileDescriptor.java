package it.doqui.libra.librabl.domain.model.files;

public interface FileDescriptor extends FileId {
    String getHash();
    Long getSize();
}
