package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.AssociationItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public interface AssociationService {
    Paged<AssociationItem> findAssociations(@NotNull String uuid, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable);
    AssociationItem findAssociation(@NotNull String parentUUID, @NotNull String childUUID);
    AsyncOperation<AssociationItem> linkNode(@NotNull String uuid, @NotNull LinkItemRequest linkItem, OperationMode mode);
    void unlinkNode(@NotNull String uuid, @NotNull LinkItemRequest linkItem);
    void renameAssociation(@NotNull String parentUUID, @NotNull String childUUID, @NotNull String name);
}
