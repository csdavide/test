package it.doqui.libra.librabl.views.association;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = EdgeItem.class)
public class LinkItem extends EdgeItem {

    @JsonSetter(nulls = Nulls.SKIP)
    private RelationshipKind relationship = RelationshipKind.PARENT;

    public LinkItem() {
        super();
    }

    public LinkItem(LinkItem item) {
        super(item);
        this.relationship = item.relationship;
    }

    public LinkItem(AssociationItem item, String uuid) {
        super();
        this.setTypeName(item.getTypeName());
        this.setName(item.getName());
        this.setHard(item.isHard());

        if (StringUtils.equals(uuid, item.getChild())) {
            this.setVertexUUID(item.getParent());
            this.relationship = item.getHard() != null ? RelationshipKind.PARENT : RelationshipKind.SOURCE;
        } else if (StringUtils.equals(uuid, item.getParent())) {
            this.setVertexUUID(item.getChild());
            this.relationship = item.getHard() != null ? RelationshipKind.CHILD : RelationshipKind.TARGET;
        }
    }

    @ToString(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(allOf = LinkItem.class)
    public static class ArchivedLinkItem extends LinkItem {
        private static boolean active;

        public static boolean isActive(LinkItem linkItem) {
            return active;
        }

        public void setActive(boolean value) {
            active = value;
        }
    }
}
