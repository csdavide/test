package it.doqui.libra.librabl.business.provider.search;

import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;

@ApplicationScoped
@Slf4j
public class SolrResultNormalizer {

    @ConfigProperty(name = "solr.retroCompatibilityMode", defaultValue = "false")
    boolean retroCompatibilityMode;

    @Inject
    ModelManager modelManager;

    public String normalizePath(String path) {
        if (retroCompatibilityMode) {
            ModelSchema schema = modelManager.getContextModel();
            StringBuffer sb = new StringBuffer();
            int p = 0;
            while (p < path.length()) {
                int pOpen = path.indexOf("{", p);
                if (pOpen < 0) {
                    break;
                }

                int pClose = path.indexOf("}", pOpen);
                if (pClose < 0) {
                    break;
                }

                sb.append(ISO9075.decode(path.substring(p, pOpen)));
                String s = path.substring(pOpen + 1, pClose);
                try {
                    URI uri = new URI(s);
                    String ns = schema.getNamespace(uri);
                    if (ns == null) {
                        throw new RuntimeException("Namespace not found");
                    }

                    sb.append(ns);
                    sb.append(":");
                } catch (RuntimeException | URISyntaxException e) {
                    sb.append("{");
                    sb.append(s);
                    sb.append("}");
                }

                p = pClose + 1;
            }

            if (p < path.length() - 1) {
                sb.append(ISO9075.decode(path.substring(p)));
            }

            return sb.toString();
        }

        return path;
    }
}
