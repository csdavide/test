package it.doqui.libra.librabl.domain.model.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Vertex {

    private VertexType type;
    private String value;

    public Vertex(VertexType type, String value) {
        this.type = type;
        this.value = value;
    }

    public Vertex(VertexType type, long value) {
        this(type, "" + value);
    }

    public String getValue() {
        return type == VertexType.PATH && value != null
                ? Arrays.stream(value.split("/"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("/","/", "/"))
                : value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vertex vertex) {
            return vertex.getType() == this.getType() && vertex.getValue().equals(this.getValue());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
