package it.doqui.libra.librabl.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.libra.librabl.application.model.acl.PermissionItem;
import it.doqui.libra.librabl.application.model.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.application.model.events.EventType;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.application.model.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.application.model.tenant.TenantItem;
import it.doqui.libra.librabl.application.ports.in.TenantUseCase;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.graph.*;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantLimit;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.ports.out.ConfigurationRepository;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.AssociationDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.PathDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.UserDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.User;
import it.doqui.libra.librabl.infrastructure.adapters.output.schema.SchemaManager;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.provisioning.SolrManager;
import it.doqui.libra.librabl.infrastructure.platform.events.SchemaEvent;
import it.doqui.libra.librabl.infrastructure.platform.security.AuthenticationManager;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.foundation.TenantRef.DEFAULT_TENANT;
import static it.doqui.libra.librabl.infrastructure.adapters.output.schema.TenantSchema.COMMON_SCHEMA;

@ApplicationScoped
@Slf4j
public class TenantService implements TenantUseCase {

    @ConfigProperty(name = "libra.multitenant.auto-create-schema.enabled", defaultValue = "false")
    boolean autoCreateSchemaEnabled;

    @ConfigProperty(name = "libra.multitenant.sql-resources-include-fk", defaultValue = "true")
    boolean includeFK;

    @ConfigProperty(name = "libra.multitenant.user-homes-required", defaultValue = "true")
    boolean userHomesRequired;

    @ConfigProperty(name = "libra.multitenant.rendition-folder-required", defaultValue = "true")
    boolean renditionFolderRequired;

    @ConfigProperty(name = "libra.authentication.new-password-alg", defaultValue = "CLEAR")
    String newPasswordAlg;

    @ConfigProperty(name = "libra.content-store.tenant-isolation", defaultValue = "true")
    boolean globalTenantIsolationEnabled;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    ConfigurationRepository configurationRepository;

    @Inject
    NodeManager nodeManager;

    @Inject
    SolrManager solrManager;

    @Inject
    SchemaManager schemaManager;

    @Inject
    ModelManagerPort modelManager;

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
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    void onStart(@Observes SchemaEvent ev) {
        if (Strings.CS.equals(ev.getType(), EventType.RELOAD_TENANT)) {
            log.trace("Reloading tenant {} (sent by {})", ev.getTenantRef(), ev.getSender());
            var tenantName = ev.getTenantRef().toString();
            if (Strings.CI.equals(tenantName, COMMON_SCHEMA) || Strings.CI.equals(tenantName, "*")) {
                tenantRepository.cleanCache();
                tenantRepository
                    .findAll()
                    .forEach(t -> schemaManager.loadTenant(TenantRef.valueOf(t.getTenant()), t.getSchema()));
            } else {
                tenantRepository.removeTenantFromCache(tenantName);
                tenantRepository
                    .findByIdOptional(tenantName)
                    .ifPresent(t -> schemaManager.loadTenant(ev.getTenantRef(), t.getSchema()));
            }
            log.info("Tenant {} reloaded", ev.getTenantRef());
        }
    }

    @Override
    public void syncTenant(TenantRef tenantRef, boolean includeAny) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);
        solrManager.createTenant(sessionContext.getUserContext().getTenantRef(), false);
        performSync(tenantRef, sessionContext.getUserContext().getDbSchema(), includeAny);
    }

    @Override
    public void deleteTenant(TenantRef tenantRef) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);
        var tenantName = tenantRef.toString();
        var schemaName = sessionContext.getUserContext().getDbSchema();
        log.info("Deleting tenant {} in the db schema {}", tenantName, schemaName);
        tenantRepository
            .listTenantsInSchema(schemaName)
            .stream()
            .filter(name -> !Strings.CI.equals(tenantName, name))
            .findAny()
            .ifPresentOrElse(tenant -> {
                    log.warn("Other tenants sharing the same db schema");
                    tenantRepository.cleanTenant(tenantName, schemaName);
                },
                () -> {
                    log.info("No other tenant is sharing the same db schema");
                    tenantRepository.destroySchema(schemaName);
                });

        solrManager.cleanTenant(tenantRef);
        tenantRepository.deleteTenant(tenantName);
        log.info("Tenant {} deleted", tenantRef);
        modelManager.reloadTenant(tenantName);
    }

    @Override
    public void performSync(TenantRef tenantRef, boolean includeAny) {
        final String dbSchema;
        if (Strings.CI.equals(sessionContext.getTenant(), tenantRef.toString())) {
            dbSchema = sessionContext.getUserContext().getDbSchema();
        } else {
            dbSchema = tenantRepository
                .findByIdOptional(tenantRef.toString())
                .map(TenantSpace::getSchema)
                .orElseThrow(() -> new RuntimeException(String.format("Tenant '%s' not found", tenantRef)));
        }

        performSync(tenantRef, dbSchema, includeAny);
    }

    @Override
    public List<String> listAllSchemas() {
        return tenantRepository.listAllSchemas();
    }

    @Override
    public TenantItem createTenant(TenantCreationRequest request) {
        return transactionManagerPort.doOnOtherContext(() -> performTenantCreation(request));
    }

    private TenantItem performTenantCreation(TenantCreationRequest request) {
        var t = createAndSync(request);
        AtomicBoolean tenantPersistRequired = new AtomicBoolean(false);
        // create root
        // create app:company_home
        // create app:user_homes
        // create cm:rendition
        // create cm:temp

        var result = transactionManagerPort.perform(tx -> {
            AtomicBoolean created = new AtomicBoolean(false);
            tenantRepository.lockTenantTable();
            var foundTenant = tenantRepository.findByIdOptional(t.getTenant());
            if (foundTenant.isEmpty() || foundTenant.get().getRootId() == null) {
                pathDAO.findNodeIdWherePath("/").ifPresentOrElse(n -> {
                    t.setRootId(n.getId());
                    log.info("Found root node {} for tenant {}", t.getRootId(), t.getTenant());
                    tenantPersistRequired.set(true);
                    tenantRepository.persist(t);
                }, () -> {
                    // create root
                    var rootNode = createRootNode(tx);

                    var admin = new User();
                    admin.setUsername("admin");
                    admin.setTenant(t.getTenant());
                    admin.setUuid(UuidCreator.getTimeOrderedEpoch().toString());
                    admin.getData().setEnabled(true);

                    if (request.getPassword() != null &&  request.getPassword().length > 0) {
                        try {
                            admin.getData().setPassword(ObjectUtils.hash(request.getPassword(), newPasswordAlg));
                            admin.getData().setAlg(newPasswordAlg);
                        } catch (NoSuchAlgorithmException e) {
                            throw new SystemException(e);
                        }
                    }

                    userDAO.createUser(admin, Strings.CS.equals(t.getTenant(), DEFAULT_TENANT));

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

                    var appCompanyHomeLink = new ParentLink();
                    appCompanyHomeLink.setBindingType(BindingType.HARD);
                    appCompanyHomeLink.setParent(new Vertex(VertexType.UUID, rootNode.getUuid()));
                    appCompanyHomeLink.setType("sys:children");
                    appCompanyHomeLink.setName("app:company_home");
                    appCompanyHome.setLink(appCompanyHomeLink);
                    var appCompanyHomeNode = nodeManager.createNode(tx, appCompanyHome, Set.of());

                    if (userHomesRequired) {
                        // create app:user_homes
                        nodeManager.createNode(tx, createFolder(appCompanyHomeNode.getUuid(), "app:user_homes", "User Homes", new PermissionsDescriptor()), Set.of());
                    }

                    if (renditionFolderRequired) {
                        // create cm:rendition
                        nodeManager.createNode(tx, createFolder(appCompanyHomeNode.getUuid(), Constants.CM_RENDITIONS, null, new PermissionsDescriptor()), Set.of());
                    }

                    // create cm:temp
                    nodeManager.createNode(tx, createTemp(appCompanyHomeNode.getUuid()), Set.of());

                    t.setRootId(rootNode.getId());
                    created.set(true);
                    tenantPersistRequired.set(true);
                    tenantRepository.persist(t);
                });
            } else {
                tenantPersistRequired.set(true);
                tenantRepository.persist(t);
                log.warn("Tenant {} already present", t.getTenant());
            }

            var isCreated = created.get();
            return PerformResult.<TenantSpace>builder()
                .result(isCreated || tenantPersistRequired.get() ? t : null)
                .count(isCreated ? 5 : 0)
                .mode(isCreated ? PerformResult.Mode.SYNC : PerformResult.Mode.NONE)
                .build();
        });

        var response = map(result);
        if (response != null) {
            modelManager.reloadTenant(response.getName());
        }

        return response;
    }

    private TenantSpace createAndSync(TenantCreationRequest request) {
        String tenantName = new TenantRef(request.getTenant()).toString();
        Optional<TenantSpace> foundTenant = tenantRepository.findByIdOptional(tenantName);
        final String schema;
        if (foundTenant.isPresent()) {
            if (StringUtils.isNotBlank(foundTenant.get().getSchema())) {
                if (StringUtils.isNotBlank(request.getSchema()) && !Strings.CS.equals(request.getSchema(), foundTenant.get().getSchema())) {
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
                tenantRepository.checkIfSchemaExists(schema);
            } catch (PreconditionFailedException e) {
                if (!autoCreateSchemaEnabled) {
                    throw e;
                }

                tenantRepository.initializeSchema(schema, includeFK);
            }
        } catch (Exception e) {
            log.error("Unable to validate schema {}: {}", schema, e.getMessage());
            throw new BadRequestException(String.format("Invalid schema '%s'", schema));
        }

        schemaManager.createTenantSchemaIfRequired(tenantName, schema);

        final TenantRef tenantRef = TenantRef.valueOf(tenantName);
        var ctx = authenticationService.loginAsAdmin(tenantRef, schema);
        solrManager.createTenant(sessionContext.getUserContext().getTenantRef(), request.isOverwrite());
        solrManager.syncTenant(tenantRef, schemaManager.getTenantSchema(sessionContext.getUserContext().getTenantRef().toString()), true);

        var data = foundTenant.map(TenantSpace::getData).orElse(new TenantData());
        data.setTemp(ObjectUtils.getIfDefined(request.getTemp(), data.getTemp(), false));
        data.setIndexingDisabled(ObjectUtils.getIfDefined(request.getIndexingDisabled(), data.isIndexingDisabled(), false));
        data.setFullTextDisabled(ObjectUtils.getIfDefined(request.getFullTextDisabled(), data.isFullTextDisabled(), false));
        data.setTempEphemeralDisabled(ObjectUtils.getIfDefined(request.getTempEphemeralDisabled(), data.isTempEphemeralDisabled(), false));
        data.setDuplicatesAllowed(ObjectUtils.getIfDefined(request.getDuplicatesAllowed(), data.isDuplicatesAllowed(), false));
        data.setTenantIsolationEnabled(globalTenantIsolationEnabled ? false : null);
        mapRequestLimits(data, request.getLimits());

        Optional.ofNullable(request.getStores()).ifPresent(stores -> data.getStores().putAll(stores));
        ctx.getAttributes().put(UserContext.TENANT_DATA_ATTR, data);

        var t = new TenantSpace();
        t.setTenant(tenantName);
        t.setSchema(schema);
        t.setData(data);
        t.setRootId(foundTenant.map(TenantSpace::getRootId).orElse(null));
        return t;
    }

    @Override
    public List<TenantItem> findStartingWith(String prefix) {
        return tenantRepository.findStartingWith(prefix).stream().map(this::map).toList();
    }

    @Override
    public Optional<TenantItem> findByIdOptional(String name, boolean authRequired) {
        if (authRequired) {
            authenticationService.autenticateIfRequired(TenantRef.valueOf(name), false);
        }
        return tenantRepository.findByIdOptional(name).map(this::map);
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

        var tempLink = new ParentLink();
        tempLink.setBindingType(BindingType.HARD);
        tempLink.setParent(new Vertex(VertexType.UUID, parentUUID));
        tempLink.setType(Constants.CM_CONTAINS);
        tempLink.setName("cm:temp");
        temp.setLink(tempLink);

        return temp;
    }

    private LinkedInputNodeRequest createFolder(String parentUUID, String name, String title, PermissionsDescriptor pd) {
        var folder = new LinkedInputNodeRequest();
        folder.setTypeName(Constants.CM_FOLDER);
        folder.getProperties().put(Constants.CM_NAME, Optional.ofNullable(title).orElse(PrefixedQName.valueOf(name).getLocalPart()));
        folder.setPermissionsDescriptor(pd);

        var link = new ParentLink();
        link.setBindingType(BindingType.HARD);
        link.setParent(new Vertex(VertexType.UUID, parentUUID));
        link.setType(Constants.CM_CONTAINS);
        link.setName(name);
        folder.setLink(link);

        return folder;
    }

    private ActiveNode createRootNode(ApplicationTransaction tx) {
        var rootNode = new ActiveNode();
        rootNode.setTenant(sessionContext.getUserContext().getTenantRef().toString());
        rootNode.setUuid(UuidCreator.getTimeOrderedEpoch().toString());
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
        if (s == null) {
            return null;
        }
        var t = new TenantItem();
        t.setName(s.getTenant());
        t.setEnabled(Optional.ofNullable(s.getData()).map(TenantData::isEnabled).orElse(true));
        t.setTemp(Optional.ofNullable(s.getData()).map(TenantData::getTemp).orElse(s.getTenant()));
        t.setIndexingDisabled(Optional.ofNullable(s.getData()).map(TenantData::isIndexingDisabled).orElse(false));
        t.setFullTextDisabled(Optional.ofNullable(s.getData()).map(TenantData::isFullTextDisabled).orElse(false));
        t.setTempEphemeralDisabled(Optional.ofNullable(s.getData()).map(TenantData::isTempEphemeralDisabled).orElse(false));
        t.setDuplicatesAllowed(Optional.ofNullable(s.getData()).map(TenantData::isDuplicatesAllowed).orElse(false));
        t.setLinkDefaultDurationSeconds(Optional.ofNullable(s.getData()).map(TenantData::getLinkDefaultDurationSeconds).orElse(0));
        t.setLinkGracePeriodSeconds(Optional.ofNullable(s.getData()).map(TenantData::getLinkGracePeriodSeconds).orElse(0));

        boolean sysadmin = Optional.ofNullable(sessionContext.getUserContext()).map(ctx -> ctx.isUserInRole(UserContext.ROLE_SYSADMIN)).orElse(false);
        if (sysadmin || s.getTenant().equals(sessionContext.getTenant())) {
            t.setProperties(new HashMap<>());
            var limits = new ArrayList<TenantLimit>();
            for (String limitKey : configurationRepository.getAllLimitPropertyKeys()) {
                var v = s.getTenant().equals(sessionContext.getTenant()) ?
                    configurationRepository.getIntegerProperty(limitKey, false)
                    : ObjectUtils.getAsInteger(configurationRepository.getStringProperty(s.getTenant(), limitKey, false), null);
                limits.add(configurationRepository.mapToTenantLimit(limitKey, v));
            }
            t.getProperties().put("limits", limits);

            t.getProperties().putAll(
                s.getData().getProperties()
                    .entrySet()
                    .stream()
                    .filter(e -> !configurationRepository.getAllLimitPropertyKeys().contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );

            if (sysadmin) {
                Optional.ofNullable(s.getData()).map(TenantData::getStores).ifPresent(stores -> t.setStores(new HashMap<>(stores)));
                t.setSchema(s.getSchema());
            }
        }
        return t;
    }

    public void mapRequestLimits(TenantData td, List<TenantLimit> requestedLimits) {
        if (requestedLimits == null || requestedLimits.isEmpty()) {
            return;
        }
        for (var requestedLimit : requestedLimits) {
            var limitProperty = configurationRepository.mapToConfigPropertyKey(requestedLimit);
            if (requestedLimit.getValue() == null) {
                td.getProperties().remove(limitProperty);
                continue;
            }
            td.getProperties().put(limitProperty, requestedLimit.getValue());
        }
    }
}
