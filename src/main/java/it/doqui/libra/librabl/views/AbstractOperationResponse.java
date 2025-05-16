package it.doqui.libra.librabl.views;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public abstract class AbstractOperationResponse<T> {
    protected T op;
    protected Object result;
}
