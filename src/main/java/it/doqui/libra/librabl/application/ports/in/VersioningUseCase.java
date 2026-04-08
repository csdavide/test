package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.application.model.graph.VersionItem;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface VersioningUseCase {
    List<VersionItem> listNodeVersions(Vertex vertex, List<String> tags);
    Optional<VersionItem> createNodeVersion(Vertex vertex, String tag);
    void alterTagVersion(Vertex vertex, int version, String tag);
    Optional<VersionItem> getNodeVersion(Vertex vertex, int version, Set<MapOption> optionSet, Locale locale);
    Optional<VersionItem> getNodeVersion(String versionUUID, Set<MapOption> optionSet, Locale locale);
    NodeAttachment getVersionedContent(Vertex vertex, int version, String contentPropertyName, String fileName);
    NodeAttachment getVersionedContent(String versionUUID, String contentPropertyName, String fileName);
    void replaceNodeMetadata(Vertex vertex, Vertex source, Integer sourceVersion);
}
