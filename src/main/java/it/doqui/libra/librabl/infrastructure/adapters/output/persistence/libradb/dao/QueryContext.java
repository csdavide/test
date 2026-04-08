package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.policy.MapOption;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class QueryContext {
    private final String schema;
    private final String tenant;

    @Builder.Default
    private final Set<MapOption> optionSet = Set.of(MapOption.DEFAULT);

    @Builder.Default
    private final QueryScope scope = QueryScope.DEFAULT;
}
