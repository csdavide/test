package it.doqui.libra.librabl.business.service.interfaces;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

public interface ContentStoreService {

    void delete(String contentUrl) throws IOException;
    Set<String> getStoresOfPath(Path path);
    Path getStorePath(String contentUrl);
    Path getPath(String contentUrl) throws IOException;
    long writeStream(String contentUrl, InputStream stream) throws IOException;

}
