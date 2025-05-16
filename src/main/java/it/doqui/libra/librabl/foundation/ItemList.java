package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ItemList<T> implements ListContainer<T> {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<T> items;

    public ItemList(List<T> items) {
        this.items = Collections.unmodifiableList(items);
    }

    protected ItemList() {
        this(List.of());
    }
}
