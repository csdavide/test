package it.doqui.libra.librabl.foundation.flow;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;

import java.util.Deque;
import java.util.LinkedList;

@RequestScoped
@Getter
public class BusinessContext {
    private final Deque<InvocationCall> stack;

    public BusinessContext() {
        this.stack = new LinkedList<>();
    }

    public record InvocationCall(String className, String methodName) {}
}
