package it.doqui.libra.librabl.application.mappers;

import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.association.LinkItem;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.domain.model.graph.BindingType;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

@ApplicationScoped
public class AssociationMapper {

    public LinkItemRequest mapParentLink(ParentLink parent) {
        var link = new LinkItemRequest();
        switch (parent.getParent().getType()) {
            case UUID:
                link.setVertexUUID(parent.getParent().getValue());
                break;

            case PATH:
                link.setPath(parent.getParent().getValue());
                break;

            case ID:
                throw new IllegalArgumentException("ID vertex type is not supported");
        }

        link.setName(parent.getName());
        link.setTypeName(parent.getType());
        link.setHard(parent.getHard());
        link.setRelationship(RelationshipKind.PARENT);
        return link;
    }

    public ParentLink mapLinkItemRequest(LinkItemRequest link) {
        assert link != null;
        if (link.getVertexUUID() != null) {
            if (link.getPath() != null) {
                throw new IllegalArgumentException("Cannot specify both vertexUUID and path");
            }

            return mapLinkItem(link);
        } else if (link.getPath() == null) {
            throw new IllegalArgumentException("Missing vertexUUID or path");
        }

        if (link.getRelationship() != RelationshipKind.PARENT) {
            throw new IllegalArgumentException("Invalid relationship kind");
        }

        var parent = new ParentLink();
        parent.setParent(new Vertex(VertexType.PATH, link.getPath()));
        parent.setName(link.getName());
        parent.setType(link.getTypeName());
        parent.setBindingType(link.isHard() ? BindingType.HARD : BindingType.SOFT);
        return parent;
    }

    public ParentLink mapLinkItem(LinkItem link) {
        assert link != null;
        if (link.getRelationship() != RelationshipKind.PARENT) {
            throw new IllegalArgumentException("Invalid relationship kind");
        }

        var parent = new ParentLink();
        parent.setParent(new Vertex(VertexType.UUID, link.getVertexUUID()));
        parent.setName(link.getName());
        parent.setType(link.getTypeName());
        parent.setBindingType(link.isHard() ? BindingType.HARD : BindingType.SOFT);
        return parent;
    }

    public ParentRelationship relationship(String uuid, LinkItemRequest link) {
        final Vertex child;
        final Vertex parent;
        var relationshipKind = Optional.ofNullable(link.getRelationship()).orElse(RelationshipKind.PARENT);
        switch (relationshipKind) {
            case PARENT, SOURCE -> {
                child = new Vertex(VertexType.UUID, uuid);
                parent = vertex(link);
            }
            case CHILD, TARGET -> {
                child = vertex(link);
                parent = new Vertex(VertexType.UUID, uuid);
            }
            default -> throw new BadRequestException("Invalid relationship kind");
        }

        var bindingType = switch (relationshipKind) {
            case PARENT, CHILD -> link.isHard() ? BindingType.HARD : BindingType.SOFT;
            case SOURCE, TARGET -> BindingType.LOOSE;
        };

        var createMissingPath = bindingType == BindingType.HARD && relationshipKind == RelationshipKind.PARENT;
        var parentLink = new ParentLink();
        parentLink.setParent(parent);
        parentLink.setName(link.getName());
        parentLink.setType(link.getTypeName());
        parentLink.setBindingType(bindingType);
        return new ParentRelationship(child, parentLink, createMissingPath);
    }

    private Vertex vertex(LinkItemRequest link) {
        if (StringUtils.isNotBlank(link.getVertexUUID())) {
            return new Vertex(VertexType.UUID, link.getVertexUUID());
        } else {
            return new Vertex(VertexType.PATH, link.getPath());
        }
    }

    public record ParentRelationship(Vertex vertex, ParentLink parentLink, boolean createMissingPath) {
    }
}
