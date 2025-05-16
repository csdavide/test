package it.doqui.libra.librabl.views.management;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SystemStatusInfo {
    private boolean ok;
    private boolean artemisReachable;
    private int feedbacks;
    private int expectedCount;
    private final List<InstanceInfo> instances;

    public SystemStatusInfo() {
        this.instances = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class InstanceInfo {
        private String name;
        private boolean ok;
        private boolean databaseReachable;
        private boolean solrReachable;
        private long heapSize;
        private long heapMaxSize;
        private long heapFreeSize;
    }
}
