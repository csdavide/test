package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.deleters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssociationDeleterFactory {

    @Inject
    UpdateModeDeleterDAO updateDeleterDAO;

    @Inject
    InsertModeDeleterDAO insertDeleterDAO;

    public AssociationDeleterDAO getAssociationDeleterDAO(boolean insertModeEnabled) {
        return insertModeEnabled ? insertDeleterDAO : updateDeleterDAO;
    }
}
