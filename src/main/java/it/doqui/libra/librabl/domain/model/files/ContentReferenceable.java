package it.doqui.libra.librabl.domain.model.files;

public interface ContentReferenceable {
    String getContentPropertyName();
    String getFileName();

    static ContentReferenceable of(String contentPropertyName, String fileName) {
        return new ContentReferenceable() {
            @Override
            public String getContentPropertyName() {
                return contentPropertyName;
            }

            @Override
            public String getFileName() {
                return fileName;
            }
        };
    }

    static ContentReferenceable of(String contentPropertyName) {
        return ContentReferenceable.of(contentPropertyName, null);
    }

    static ContentReferenceable of() {
        return ContentReferenceable.of(null, null);
    }
}
