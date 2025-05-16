package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.cxf.mappers.ContentMapper;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.AssociationService;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.interfaces.NodeService;
import it.doqui.libra.librabl.business.service.interfaces.VersionService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.association.AssociationItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.*;
import it.doqui.libra.librabl.views.version.VersionItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.util.*;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_WORKINGCOPY_OWNER;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.PROP_CM_COPIED_NODE;
import static it.doqui.libra.librabl.views.node.MapOption.*;

@ApplicationScoped
@Slf4j
public class VersionServiceBridge extends AbstractServiceBridge {

    @Inject
    VersionService versionService;

    @Inject
    ContentMapper contentMapper;

    @Inject
    NodeService nodeService;

    @Inject
    AssociationService associationService;

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Node cancelCheckOutContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        CheckInCheckOutException, EcmEngineTransactionException, PermissionDeniedException {
        validate(node);
        return call(context, () -> {
            var wci = findWorkingCopy(node);

            try {
                nodeService.deleteNode(node.getUid(), DeleteMode.PURGE);
            } catch (Exception e) {
                throw new CheckInCheckOutException(String.format("Unable to delete working copy %s: %s", node.getUid(), e.getMessage()));
            }

            return new Node(wci.source());
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Node checkInContent(Node workingCopy, MtomOperationContext context)
        throws InvalidParameterException, CheckInCheckOutException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(workingCopy);
        return call(context, () -> TransactionService.current().perform(tx -> {
            var wci = findWorkingCopy(workingCopy);
            var uuid = wci.source();
            try {
                versionService.createNodeVersion(uuid, null);
                versionService.replaceNodeMetadata(uuid, workingCopy.getUid(), null);
                nodeService.deleteNode(workingCopy.getUid(), DeleteMode.PURGE);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw new CheckInCheckOutException(String.format("Unable to perform checkout of working copy %s: %s", workingCopy.getUid(), e.getMessage()));
            }

            return PerformResult.<Node>builder()
                .mode(PerformResult.Mode.SYNC)
                .result(new Node(uuid))
                .count(1)
                .priorityUUIDs(Set.of(uuid))
                .build();
        }));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node checkOutContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, CheckInCheckOutException, NoSuchNodeException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        validate(node);
        return call(context, () -> TransactionService.current().perform(tx -> {
            var association = associationService
                .findAssociations(node.getUid(), RelationshipKind.PARENT, null, null, null)
                .getItems()
                .stream()
                .filter(AssociationItem::getHard)
                .findFirst()
                .orElseThrow(() -> new NoSuchNodeException(node.getUid()));

            var link = new LinkItemRequest();
            link.setTypeName(association.getTypeName());
            link.setHard(association.isHard());
            link.setVertexUUID(association.getParent());
            link.setRelationship(RelationshipKind.PARENT);

            var i = association.getName().indexOf("_wc_");
            final String name;
            if (i < 0) {
                name = association.getName();
            } else {
                name = association.getName().substring(0, i);
            }

            link.setName(String.format("%s_wc_%d", name, System.currentTimeMillis()));
            var wcUUID = nodeService.copyNode(node.getUid(), link, false, false, null);

            var wcInput = new InputNodeRequest();
            wcInput.getAspectOperations().put(Constants.ASPECT_CM_WORKINGCOPY, InputNodeRequest.AspectOperation.ADD);
            wcInput.getProperties().put(CM_WORKINGCOPY_OWNER, UserContextManager.getContext().getAuthority());
            nodeService.updateNode(wcUUID, wcInput, Set.of(OperationOption.ALLOW_MANAGED_PROPERTIES));

            return PerformResult.<Node>builder()
                .mode(PerformResult.Mode.SYNC)
                .result(new Node(wcUUID))
                .count(1)
                .priorityUUIDs(Set.of(wcUUID))
                .build();
        }));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void revertVersion(Node node, String versionLabel, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        EcmEngineTransactionException, PermissionDeniedException, EcmEngineException {
        validate(node);
        validate(() -> Objects.requireNonNull(versionLabel, "Version label cannot be null"));
        call(context, () -> {
            final int version;
            if (versionLabel.startsWith("1.")) {
                try {
                    version = Integer.parseInt(versionLabel.substring(2));
                } catch (NumberFormatException e) {
                    throw new InvalidParameterException("Invalid version label: " + versionLabel);
                }
            } else if (versionLabel.startsWith("#")) {
                var tag = versionLabel.substring(1);
                version = versionService.listNodeVersions(node.getUid(), List.of(tag))
                    .stream()
                    .map(VersionItem::getVersion)
                    .findFirst()
                    .orElseThrow(() -> new EcmEngineTransactionException("Version with tag '" + tag + "' not available"));
            } else {
                throw new InvalidParameterException("Invalid version label: " + versionLabel);
            }

            versionService.replaceNodeMetadata(node.getUid(), null, version);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Content getVersionMetadata(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException,
        EcmEngineTransactionException, ReadException, InvalidCredentialsException {
        validate(node);
        return call(context, () -> versionService
            .getNodeVersion(node.getUid(), Set.of(PARENT_ASSOCIATIONS, SYS_PROPERTIES, LEGACY), Locale.getDefault())
            .map(VersionItem::getItem)
            .map(n -> contentMapper.asContent(n))
            .orElseThrow(() -> new NotFoundException(node.getUid())));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public byte[] retrieveVersionContentData(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, ReadException,
        EcmEngineTransactionException, PermissionDeniedException {
        validate(node);
        String cname = content.getContentPropertyPrefixedName();
        validate(() -> {
            Objects.requireNonNull(content, "Content node must not be null");
            Objects.requireNonNull(cname, "Content property name must not be null");

            PrefixedQName qname = PrefixedQName.valueOf(cname);
            if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                throw new InvalidParameterException("Invalid content property name: " + cname);
            }
        });

        return call(context, () -> {
            NodeAttachment a = versionService.getVersionedContent(node.getUid(), cname, null);
            try (FileInputStream is = new FileInputStream(a.getFile())) {
                return is.readAllBytes();
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Version getVersion(Node node, String versionLabel, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, EcmEngineTransactionException, EcmEngineException,
        InvalidCredentialsException, PermissionDeniedException {
        validate(node);
        validate(() -> Objects.requireNonNull(versionLabel, "Version label cannot be null"));

        return call(context, () -> {
            if (versionLabel.startsWith("1.")) {
                try {
                    var version = Integer.parseInt(versionLabel.substring(2));
                    return versionService
                        .getNodeVersion(node.getUid(), version, Set.of(MapOption.DEFAULT), null)
                        .map(this::map)
                        .orElseThrow(() -> new EcmEngineTransactionException("Version '" + versionLabel + "' not available"));
                } catch (NumberFormatException e) {
                    throw new InvalidParameterException("Invalid version label: " + versionLabel);
                }
            } else if (versionLabel.startsWith("#")) {
                var tag = versionLabel.substring(1);
                return versionService.listNodeVersions(node.getUid(), List.of(tag))
                    .stream()
                    .map(this::map)
                    .findFirst()
                    .orElseThrow(() -> new EcmEngineTransactionException("Version with tag '" + tag + "' not available"));
            } else {
                throw new InvalidParameterException("Invalid version label: " + versionLabel);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Version[] getAllVersions(Node node, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        EcmEngineTransactionException, EcmEngineException, PermissionDeniedException {
        validate(node);
        return call(context, () -> {
            var versions = versionService.listNodeVersions(node.getUid(), null);
            log.debug("Got versions {}", versions);
            return versions.stream()
                .map(this::map)
                .toList()
                .toArray(new Version[0]);
        });
    }

    private Version map(VersionItem vi) {
        var vv = new Version();
        vv.setVersionedNode(new Node(vi.getVersionUUID()));
        vv.setVersionLabel(String.format("1.%d", vi.getVersion()));
        vv.setCreatedDate(Optional.ofNullable(vi.getCreatedAt()).map(d -> new Date(d.toInstant().toEpochMilli())).orElse(null));
        vv.setCreator(vi.getCreatedBy());
        vv.setDescription(vi.getVersionTag());

        var properties = new ArrayList<Property>();
        properties.add(new Property("versionNumber", false, ObjectUtils.strings(vi.getVersion())));
        properties.add(new Property("name", false, ObjectUtils.strings(vi.getVersionTag())));
        properties.add(new Property("frozenNodeId", false, ObjectUtils.strings(vi.getNodeUUID())));
        vv.setVersionProperties(properties.toArray(new Property[0]));

        return vv;
    }

    private WorkingCopyItem findWorkingCopy(Node node) {
        var wc = nodeService
            .getNodeMetadata(node.getUid(), Set.of(DEFAULT), null, null)
            .orElseThrow(() -> new NoSuchNodeException(node.getUid()));

        if (!wc.getAspects().contains(Constants.ASPECT_CM_WORKINGCOPY)) {
            throw new InvalidParameterException("The node is not a working copy node");
        }

        var source = Optional.ofNullable(wc.getProperties().get(PROP_CM_COPIED_NODE))
            .map(ObjectUtils::getAsString)
            .filter(StringUtils::isNotBlank)
            .map(s -> {
                var a = s.split("/");
                return a[a.length - 1];
            })
            .orElseThrow(() -> new CheckInCheckOutException("Unable to find " + PROP_CM_COPIED_NODE + " in the working copy"));

        return new WorkingCopyItem(wc, source);
    }

    private record WorkingCopyItem (NodeItem wc, String source) {}
}
