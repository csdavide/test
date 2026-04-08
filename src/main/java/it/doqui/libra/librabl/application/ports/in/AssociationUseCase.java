package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.application.model.association.AssociationItem;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public interface AssociationUseCase {
    Paged<AssociationItem> findAssociations(@NotNull String uuid, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable);
    AssociationItem findAssociation(@NotNull String parentUUID, @NotNull String childUUID);
    AsyncOperation<AssociationItem> linkNode(@NotNull String uuid, @NotNull LinkItemRequest linkItem, OperationMode mode);
    LinkResult linkNode(Vertex vertex, ParentLink link, boolean createMissingPath);
    long unlinkNode(Vertex vertex, ParentLink link);
    void renameAssociation(@NotNull String parentUUID, @NotNull String childUUID, @NotNull String name);

    record LinkResult(AssociationItem association, long affectedNodes) { }
}
