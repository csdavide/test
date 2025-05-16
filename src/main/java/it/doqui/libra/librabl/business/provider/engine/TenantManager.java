package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.AssociationDAO;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.PathDAO;
import it.doqui.libra.librabl.business.provider.data.dao.UserDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.User;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.EventType;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.SchemaEvent;
import it.doqui.libra.librabl.business.provider.integration.solr.SolrManager;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.provider.schema.impl.SchemaManager;
import it.doqui.libra.librabl.business.provider.security.AuthenticationManager;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.interfaces.TenantService;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import it.doqui.libra.librabl.views.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.views.tenant.TenantData;
import it.doqui.libra.librabl.views.tenant.TenantItem;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static it.doqui.libra.librabl.business.provider.schema.impl.TenantSchema.COMMON_SCHEMA;
import static it.doqui.libra.librabl.foundation.TenantRef.DEFAULT_TENANT;

@ApplicationScoped
@Slf4j
public class TenantManager implements TenantService {

    @ConfigProperty(name = "libra.multitenant.auto-create-schema.enabled", defaultValue = "false")
    boolean autoCreateSchemaEnabled;

    @ConfigProperty(name = "libra.multitenant.sql-resources-path", defaultValue = "true")
    boolean includeFK;

    @ConfigProperty(name = "libra.multitenant.user-homes-required", defaultValue = "true")
    boolean userHomesRequired;

    @ConfigProperty(name = "libra.multitenant.rendition-folder-required", defaultValue = "true")
    boolean renditionFolderRequired;

    @Inject
    TenantDataManager tenantDataManager;

    @Inject
    NodeManager nodeManager;

    @Inject
    SolrManager solrManager;

    @Inject
    SchemaManager schemaManager;

    @Inject
    AuthenticationManager authenticationService;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    AssociationDAO associationDAO;

    @Inject
    PathDAO pathDAO;

    @Inject
    UserDAO userDAO;

    void onStart(@Observes SchemaEvent ev) {
        if (StringUtils.equals(ev.getType(), EventType.RELOAD_TENANT)) {
            log.info("Reloading tenant {} (sent by {})", ev.getTenantRef(), ev.getSender());
            var tenantName = ev.getTenantRef().toString();
            if (StringUtils.equals(tenantName, COMMON_SCHEMA) || StringUtils.equals(tenantName, "*")) {
                tenantDataManager.cleanCache();
                tenantDataManager
                    .findAll()
                    .forEach(t -> schemaManager.loadTenant(TenantRef.valueOf(t.getTenant()), t.getSchema()));
            } else {
                tenantDataManager.removeTenantFromCache(tenantName);
                var t = tenantDataManager
                    .findByIdOptional(tenantName)
                    .orElseThrow(() -> new RuntimeException(String.format("Tenant '%s' not found", ev.getTenantRef())));
                schemaManager.loadTenant(ev.getTenantRef(), t.getSchema());
            }
        }
    }

    @Override
    public void syncTenant(TenantRef tenantRef, boolean includeAny) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);
        solrManager.createTenant(UserContextManager.getContext().getTenantRef(), false);
        performSync(tenantRef, UserContextManager.getContext().getDbSchema(), includeAny);
    }

    @Override
    public void deleteTenant(TenantRef tenantRef) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);
        var tenantName = tenantRef.toString();
        var schemaName = UserContextManager.getContext().getDbSchema();
        log.info("Deleting tenant {} in the db schema {}", tenantName, schemaName);
        tenantDataManager
            .listTenantsInSchema(schemaName)
            .stream()
            .filter(name -> !StringUtils.equalsIgnoreCase(tenantName, name))
            .findAny()
            .ifPresentOrElse(tenant -> {
                    log.warn("Other tenants sharing the same db schema");
                    tenantDataManager.cleanTenant(tenantName, schemaName);
                },
                () -> {
                    log.info("No other tenant is sharing the same db schema");
                    tenantDataManager.destroySchema(schemaName);
                });

        solrManager.cleanTenant(tenantRef);
        tenantDataManager.deleteTenant(tenantName);
        log.info("Tenant {} deleted", tenantRef);
    }

    @Override
    public void performSync(TenantRef tenantRef, boolean includeAny) {
        final String dbSchema;
        if (StringUtils.equalsIgnoreCase(UserContextManager.getTenant(), tenantRef.toString())) {
            dbSchema = UserContextManager.getContext().getDbSchema();
        } else {
            dbSchema = tenantDataManager
                .findByIdOptional(tenantRef.toString())
                .map(TenantSpace::getSchema)
                .orElseThrow(() -> new RuntimeException(String.format("Tenant '%s' not found", tenantRef)));
        }

        performSync(tenantRef, dbSchema, includeAny);
    }

    @Override
    public List<String> listAllSchemas() {
        return tenantDataManager.listAllSchemas();
    }

    @Override
    public TenantSpace createTenant(TenantCreationRequest request) {
        var t = createAndSync(request);
        AtomicBoolean tenantPersistRequired = new AtomicBoolean(false);
        // create root
        // create app:company_home
        // create app:user_homes
        // create cm:rendition
        // create cm:temp

        return TransactionService.current().perform(tx -> {
            AtomicBoolean created = new AtomicBoolean(false);
            tenantDataManager.lockTenantTable();
            var foundTenant = tenantDataManager.findByIdOptional(t.getTenant());
            if (foundTenant.isEmpty() || foundTenant.get().getRootId() == null) {
                pathDAO.findNodeIdWherePath("/").ifPresentOrElse(n -> {
                    t.setRootId(n.getId());
                    log.info("Found root node {} for tenant {}", t.getRootId(), t.getTenant());
                    tenantPersistRequired.set(true);
                    tenantDataManager.persist(t);
                }, () -> {
                    // create root
                    var rootNode = createRootNode(tx);

                    var admin = new User();
                    admin.setUsername("admin");
                    admin.setTenant(t.getTenant());
                    admin.setUuid(UUID.randomUUID().toString());
                    admin.getData().setEnabled(true);
                    userDAO.createUser(admin, StringUtils.equals(t.getTenant(), DEFAULT_TENANT));

                    // create app:company_home
                    var everyOneRead = new PermissionItem();
                    everyOneRead.setAuthority(UserContext.GROUP_EVERYONE);
                    everyOneRead.setRights(PermissionFlag.formatAsBinary(PermissionFlag.R.getValue()));

                    var pdHome = new PermissionsDescriptor();
                    pdHome.setInheritance(false);
                    pdHome.getPermissions().add(everyOneRead);

                    var appCompanyHome = new LinkedInputNodeRequest();
                    appCompanyHome.setTypeName(Constants.CM_FOLDER);
                    appCompanyHome.getProperties().put(Constants.CM_NAME, "Company Home");
                    appCompanyHome.getAspects().add(Constants.ASPECT_ECMSYS_INDEXING_REQUIRED);
                    appCompanyHome.setPermissionsDescriptor(pdHome);

                    var appCompanyHomeLink = new LinkItemRequest();
                    appCompanyHomeLink.setRelationship(RelationshipKind.PARENT);
                    appCompanyHomeLink.setVertexUUID(rootNode.getUuid());
                    appCompanyHomeLink.setTypeName("sys:children");
                    appCompanyHomeLink.setName("app:company_home");
                    appCompanyHomeLink.setHard(true);
                    appCompanyHome.getAssociations().add(appCompanyHomeLink);
                    var appCompanyHomeNode = nodeManager.createNode(tx, appCompanyHome);

                    if (userHomesRequired) {
                        // create app:user_homes
                        nodeManager.createNode(tx, createFolder(appCompanyHomeNode.getUuid(), "app:user_homes", "User Homes", new PermissionsDescriptor()));
                    }

                    if (renditionFolderRequired) {
                        // create cm:rendition
                        nodeManager.createNode(tx, createFolder(appCompanyHomeNode.getUuid(), Constants.CM_RENDITIONS, null, new PermissionsDescriptor()));
                    }

                    // create cm:temp
                    nodeManager.createNode(tx, createTemp(appCompanyHomeNode.getUuid()));

                    t.setRootId(rootNode.getId());
                    created.set(true);
                    tenantPersistRequired.set(true);
                    tenantDataManager.persist(t);
                });
            } else {
                log.warn("Tenant {} already present", t.getTenant());
            }

            var isCreated = created.get();
            return PerformResult.<TenantSpace>builder()
                .result(isCreated || tenantPersistRequired.get() ? t : null)
                .count(isCreated ? 5 : 0)
                .mode(isCreated ? PerformResult.Mode.SYNC : PerformResult.Mode.NONE)
                .build();
        });
    }

    private TenantSpace createAndSync(TenantCreationRequest request) {
        String tenantName = new TenantRef(request.getTenant()).toString();
        Optional<TenantSpace> foundTenant = tenantDataManager.findByIdOptional(tenantName);
        final String schema;
        if (foundTenant.isPresent()) {
            if (StringUtils.isNotBlank(foundTenant.get().getSchema())) {
                if (StringUtils.isNotBlank(request.getSchema()) && !StringUtils.equals(request.getSchema(), foundTenant.get().getSchema())) {
                    throw new BadRequestException("The schema cannot be changed");
                }

                schema = foundTenant.get().getSchema();
            } else if (StringUtils.isBlank(request.getSchema())) {
                throw new BadRequestException("No schema specified");
            } else {
                schema = request.getSchema();
            }
        } else if (StringUtils.isBlank(request.getSchema())) {
            throw new BadRequestException("No schema specified");
        } else {
            schema = request.getSchema();
        }

        try {
            try {
                tenantDataManager.checkIfSchemaExists(schema);
            } catch (PreconditionFailedException e) {
                if (!autoCreateSchemaEnabled) {
                    throw e;
                }

                tenantDataManager.initializeSchema(schema, includeFK);
            }
        } catch (Exception e) {
            log.error("Unable to validate schema {}: {}", schema, e.getMessage());
            throw new BadRequestException(String.format("Invalid schema '%s'", schema));
        }

        schemaManager.createTenantSchemaIfRequired(tenantName, schema);

        final TenantRef tenantRef = TenantRef.valueOf(tenantName);
        var ctx = authenticationService.loginAsAdmin(tenantRef, schema);
        solrManager.createTenant(UserContextManager.getContext().getTenantRef(), request.isOverwrite());
        solrManager.syncTenant(tenantRef, schemaManager.getTenantSchema(UserContextManager.getContext().getTenantRef().toString()));

        var data = foundTenant.map(TenantSpace::getData).orElse(new TenantData());
        data.setTemp(ObjectUtils.getIfDefined(request.getTemp(), data.getTemp(), false));
        data.setIndexingDisabled(ObjectUtils.getIfDefined(request.getIndexingDisabled(), data.isIndexingDisabled(), false));
        data.setFullTextDisabled(ObjectUtils.getIfDefined(request.getFullTextDisabled(), data.isFullTextDisabled(), false));
        data.setTempEphemeralDisabled(ObjectUtils.getIfDefined(request.getTempEphemeralDisabled(), data.isTempEphemeralDisabled(), false));
        data.setDuplicatesAllowed(ObjectUtils.getIfDefined(request.getDuplicatesAllowed(), data.isDuplicatesAllowed(), false));
        Optional.ofNullable(request.getStores()).ifPresent(stores -> data.getStores().putAll(stores));
        ctx.getAttributes().put(UserContext.TENANT_DATA_ATTR, data);

        var t = new TenantSpace();
        t.setTenant(tenantName);
        t.setSchema(schema);
        t.setData(data);
        return t;
    }

    @Override
    public List<TenantItem> findStartingWith(String prefix) {
        return tenantDataManager.findStartingWith(prefix).stream().map(this::map).toList();
    }

    @Override
    public Optional<TenantItem> findByIdOptional(String name) {
        return tenantDataManager.findByIdOptional(name).map(this::map);
    }

    private LinkedInputNodeRequest createTemp(String parentUUID) {
        // create temp SG
        var everyOneWrite = new PermissionItem();
        everyOneWrite.setAuthority(UserContext.GROUP_EVERYONE);
        everyOneWrite.setRights(PermissionFlag.formatAsBinary(PermissionFlag.parse("RWCD")));
        var pd = new PermissionsDescriptor();
        pd.setInheritance(true);
        pd.getPermissions().add(everyOneWrite);

        // create cm:temp
        var temp = new LinkedInputNodeRequest();
        temp.setTypeName(Constants.CM_FOLDER);
        temp.getProperties().put(Constants.CM_NAME, "temp");
        temp.getAspects().add(Constants.ASPECT_ECMSYS_INDEXING_REQUIRED);
        temp.setPermissionsDescriptor(pd);

        var tempLink = new LinkItemRequest();
        tempLink.setRelationship(RelationshipKind.PARENT);
        tempLink.setVertexUUID(parentUUID);
        tempLink.setTypeName(Constants.CM_CONTAINS);
        tempLink.setName("cm:temp");
        tempLink.setHard(true);
        temp.getAssociations().add(tempLink);

        return temp;
    }

    private LinkedInputNodeRequest createFolder(String parentUUID, String name, String title, PermissionsDescriptor pd) {
        var folder = new LinkedInputNodeRequest();
        folder.setTypeName(Constants.CM_FOLDER);
        folder.getProperties().put(Constants.CM_NAME, Optional.ofNullable(title).orElse(PrefixedQName.valueOf(name).getLocalPart()));
        folder.setPermissionsDescriptor(pd);

        var link = new LinkItemRequest();
        link.setRelationship(RelationshipKind.PARENT);
        link.setVertexUUID(parentUUID);
        link.setTypeName(Constants.CM_CONTAINS);
        link.setName(name);
        link.setHard(true);
        folder.getAssociations().add(link);

        return folder;
    }

    private ActiveNode createRootNode(ApplicationTransaction tx) {
        var rootNode = new ActiveNode();
        rootNode.setTenant(UserContextManager.getContext().getTenantRef().toString());
        rootNode.setUuid(UUID.randomUUID().toString());
        rootNode.setTypeName("sys:store_root");
        rootNode.getData().getAspects().add("sys:aspect_root");
        rootNode.getData().getAspects().add(Constants.ASPECT_ECMSYS_INDEXING_REQUIRED);
        rootNode.setTx(tx);
        rootNode.setTransactionFlags(IndexingFlags.formatAsBinary(IndexingFlags.METADATA_FLAG));
        nodeDAO.createNode(rootNode);
        associationDAO.createRootPath(rootNode);
        return rootNode;
    }

    private void performSync(TenantRef tenantRef, String dbSchema, boolean includeAny) {
        try {
            log.info("Reloading tenant {}", tenantRef);
            schemaManager.loadTenant(tenantRef, dbSchema);
            if (includeAny) {
                solrManager.syncTenant(tenantRef, schemaManager.getTenantSchema(COMMON_SCHEMA), true);
            }

            solrManager.syncTenant(tenantRef, schemaManager.getTenantSchema(tenantRef.toString()));
        } catch (WebException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    private TenantItem map(TenantSpace s) {
        var t = new TenantItem();
        t.setName(s.getTenant());
        t.setEnabled(Optional.ofNullable(s.getData()).map(TenantData::isEnabled).orElse(true));
        t.setTemp(Optional.ofNullable(s.getData()).map(TenantData::getTemp).orElse(s.getTenant()));
        t.setIndexingDisabled(Optional.ofNullable(s.getData()).map(TenantData::isIndexingDisabled).orElse(false));
        t.setFullTextDisabled(Optional.ofNullable(s.getData()).map(TenantData::isFullTextDisabled).orElse(false));
        t.setTempEphemeralDisabled(Optional.ofNullable(s.getData()).map(TenantData::isTempEphemeralDisabled).orElse(false));
        t.setDuplicatesAllowed(Optional.ofNullable(s.getData()).map(TenantData::isDuplicatesAllowed).orElse(false));

        if (Optional.ofNullable(UserContextManager.getContext()).map(ctx -> ctx.isUserInRole(UserContext.ROLE_SYSADMIN)).orElse(false)) {
            Optional.ofNullable(s.getData()).map(TenantData::getStores).ifPresent(stores -> t.setStores(new HashMap<>(stores)));
            t.setSchema(s.getSchema());
        }
        return t;
    }

}
