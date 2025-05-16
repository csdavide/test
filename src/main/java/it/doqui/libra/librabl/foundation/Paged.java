package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public class Paged<T> {

    private final long page;
    private final long size;

    private final long totalElements;
    private final long totalPages;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<T> items;

    public Paged(long index, long size, long totalElements, long totalPages, List<T> items) {
        this.page = index;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.items = Collections.unmodifiableList(items);
    }

    public Paged(long index, long size, long totalElements, List<T> items) {
        this.page = index;
        this.size = size;
        this.totalElements = totalElements;

        if (size > 0) {
            this.totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        } else {
            this.totalPages =  1;
        }

        this.items = Collections.unmodifiableList(items);
    }

    protected Paged() {
        this(0, 0, 0, 1, List.of());
    }

    public Paged(List<T> items) {
        this(0, items.size(), items.size(), 1, items);
    }

    public <R> Paged<R> map(Function<T, R> mapper) {
        var mapped = items.stream().map(mapper).filter(Objects::nonNull).collect(Collectors.toList());
        return new Paged<>(this.page, this.size, this.totalElements, this.totalPages, mapped);
    }
}
