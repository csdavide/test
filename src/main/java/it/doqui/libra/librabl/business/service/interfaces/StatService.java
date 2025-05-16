package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.provider.stats.StatMeasure;

public interface StatService {
    StatMeasure getAggregatedStatMeasure();
}
