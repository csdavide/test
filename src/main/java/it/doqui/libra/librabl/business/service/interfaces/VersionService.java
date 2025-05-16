package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.version.VersionItem;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface VersionService {
    List<VersionItem> listNodeVersions(String uuid, List<String> tags);
    Optional<VersionItem> createNodeVersion(String uuid, String tag);
    void alterTagVersion(String uuid, int version, String tag);
    Optional<VersionItem> getNodeVersion(String uuid, int version, Set<MapOption> optionSet, Locale locale);
    Optional<VersionItem> getNodeVersion(String versionUUID, Set<MapOption> optionSet, Locale locale);
    NodeAttachment getVersionedContent(String uuid, int version, String contentPropertyName, String fileName);
    NodeAttachment getVersionedContent(String versionUUID, String contentPropertyName, String fileName);
    void replaceNodeMetadata(String uuid, String sourceUUID, Integer sourceVersion);
}
