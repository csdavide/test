package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.cxf.mappers.AclMapper;
import it.doqui.libra.librabl.api.v1.cxf.mappers.ContentMapper;
import it.doqui.libra.librabl.business.service.exceptions.SearchEngineException;
import it.doqui.libra.librabl.business.service.interfaces.*;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.business.service.node.SortDefinition;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.exceptions.BadQueryException;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.common.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_CONTENT;
import static it.doqui.libra.librabl.views.node.MapOption.*;
import static it.doqui.libra.librabl.views.node.OperationOption.*;

@ApplicationScoped
@Slf4j
public class NodeServiceBridge extends AbstractServiceBridge {

    @Inject
    NodeService nodeService;

    @Inject
    SearchService searchService;

    @Inject
    ContentMapper contentMapper;

    @Inject
    AclMapper aclMapper;

    @Inject
    MultipleNodeOperationService multipleNodeOperationService;

    @Inject
    TemporaryService temporaryService;

    @Inject
    ArchiveService archiveService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    // Retrieve Node Info
    public long getDbIdFromUID(Node node, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, ReadException {

        return getNodeInfo(node, context).getId();
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public TransactionInfo getTxnInfoFromDbid(long dbid, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException {
        return call(context, () -> nodeService
            .getNodeMetadata(dbid, Set.of(TX), null, Locale.getDefault())
            .map(n -> {
                var result = new TransactionInfo();
                result.setNodeId(n.getId());
                result.setUid(n.getUuid());

                if (n.getTx() != null) {
                    result.setTransactionId(n.getTx().getId());
                }

                return result;
            })
            .orElseThrow(() -> new NotFoundException("DBID: " + dbid)));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public TransactionInfo getTxnInfoFromUID(Node node, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException {
        validate(node);
        return call(context, () -> nodeService
            .getNodeMetadata(node.getUid(), Set.of(TX), null, Locale.getDefault())
            .map(n -> {
                var result = new TransactionInfo();
                result.setNodeId(n.getId());
                result.setUid(n.getUuid());

                if (n.getTx() != null) {
                    result.setTransactionId(n.getTx().getId());
                }

                return result;
            })
            .orElseThrow(() -> new NotFoundException(node.getUid())));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String getTypePrefixedName(Node node, MtomOperationContext context) throws InvalidParameterException,
        NoSuchNodeException, PermissionDeniedException, InvalidCredentialsException, RemoteException {

        return getNodeInfo(node, context).getTypeName();
    }

    private NodeInfoItem getNodeInfo(Node node, MtomOperationContext context) throws InvalidParameterException,
        NoSuchNodeException, PermissionDeniedException, InvalidCredentialsException {

        validate(node);
        return call(context, () -> nodeService
            .getNodeInfo(node.getUid())
            .orElseThrow(() -> new NotFoundException(node.getUid())));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    // Retrieve Node Metadata
    public Content getContentMetadata(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException,
        EcmEngineTransactionException, ReadException, InvalidCredentialsException {

        validate(node);
        return call(context, () -> nodeService
            .getNodeMetadata(node.getUid(), Set.of(PARENT_ASSOCIATIONS, SYS_PROPERTIES, NO_NULL_PROPERTIES, LEGACY), null, Locale.getDefault())
            .map(n -> contentMapper.asContent(n))
            .orElseThrow(() -> new NotFoundException(node.getUid())));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Path[] getPaths(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, SearchException, InvalidCredentialsException {

        validate(node);
        return call(context, () -> nodeService.listNodePaths(node.getUid())
            .stream()
            .map(x -> {
                Path y = new Path();
                y.setPath(x.getPath());
                y.setPrimary(x.isHard());
                return y;
            })
            .toList()
            .toArray(new Path[0]));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Content[] massiveGetContentMetadata(Node[] nodes, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        return massiveGetContentMetadata(nodes, null, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Content[] massiveGetContentMetadataPartial(Node[] nodes, Property[] properties, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        if (properties != null && properties.length ==  0) {
            properties = null;
        }

        return massiveGetContentMetadata(nodes, properties, context);
    }

    private Content[] massiveGetContentMetadata(Node[] nodes, Property[] properties, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        if (nodes == null || nodes.length == 0) {
            return null;
        }

        validate(nodes);
        validate(() -> {
            if (Arrays.stream(nodes).map(Node::getUid).distinct().toList().size() < nodes.length) {
                throw new InvalidParameterException("Duplicate nodes");
            }
        });
        return call(context, () -> {
            final List<String> filterPropertyNames = properties == null
                ? null
                : Arrays.stream(properties).filter(Objects::nonNull).map(ContentItem::getPrefixedName).toList();

            List<NodeItem> foundNodes = nodeService
                .listNodeMetadata(
                    Arrays.stream(nodes).map(Node::getUid).collect(Collectors.toList()),
                    Set.of(PARENT_ASSOCIATIONS, SYS_PROPERTIES, NO_NULL_PROPERTIES, LEGACY, ACL),
                    ObjectUtils.asNullableSet(filterPropertyNames),
                    Locale.getDefault(),
                    QueryScope.DEFAULT
                );

            if (foundNodes.size() != nodes.length) {
                Set<String> requestedUUIDs = Arrays.stream(nodes).map(Node::getUid).collect(Collectors.toSet());
                Set<String> foundUUIDs = foundNodes.stream().map(NodeItem::getUuid).collect(Collectors.toSet());
                throw new NoSuchNodeException(String.join(",", CollectionUtils.diff(requestedUUIDs, foundUUIDs)));
            }

            return foundNodes
                .stream()
                .map(n -> contentMapper.asContent(n, filterPropertyNames))
                .toArray(Content[]::new);
        });
    }

    private Pair<List<SortDefinition>, Pageable> prepareSearch(SearchParams lucene) {
        validate(() -> {
            Objects.requireNonNull(lucene, "Lucene must not be null");
            Objects.requireNonNull(StringUtils.stripToNull(lucene.getLuceneQuery()), "Query must not be null");
        });

        List<SortDefinition> sortFields = lucene.getSortFields() == null ? null : Arrays.stream(lucene.getSortFields())
            .filter(Objects::nonNull)
            .filter(s -> StringUtils.isNotBlank(s.getFieldName()))
            .map(s -> SortDefinition.builder().fieldName(s.getFieldName()).ascending(s.isAscending()).build())
            .collect(Collectors.toList());

        Pageable pageable = new Pageable();
        if (lucene.getPageSize() > 0) {
            pageable.setSize(lucene.getPageSize());
            pageable.setPage(lucene.getPageIndex());
        } else {
            pageable.setPage(0);
            pageable.setSize(lucene.getLimit());
        }

        return new ImmutablePair<>(sortFields, pageable);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public NodeResponse luceneSearchNoMetadata(SearchParams lucene, MtomOperationContext context)
        throws InvalidParameterException, TooManyResultsException, SearchException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        return call(context, () -> {
            var s = prepareSearch(lucene);
            var p = searchService.findNodes(StringUtils.stripToEmpty(lucene.getLuceneQuery()), s.getLeft(), s.getRight());

            NodeResponse result = new NodeResponse();
            result.setTotalResults((int)p.getTotalElements());
            result.setPageIndex((int)p.getPage());
            result.setPageSize((int)p.getSize());

            result.setNodeArray(
                p.getItems().stream()
                    .map(uuid -> {
                        Node node = new Node();
                        node.setUid(uuid);
                        return node;
                    }).toArray(Node[]::new)
            );

            return result;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public SearchResponse luceneSearch(SearchParams lucene, MtomOperationContext context) throws SystemException {
        try {
            return call(context, () -> {
                var s = prepareSearch(lucene);
                var p = searchService.findNodes(StringUtils.stripToEmpty(lucene.getLuceneQuery()), s.getLeft(), Set.of(PARENT_ASSOCIATIONS, SYS_PROPERTIES, NO_NULL_PROPERTIES, LEGACY), null, Locale.getDefault(), s.getRight());

                SearchResponse result = new SearchResponse();
                result.setTotalResults((int)p.getTotalElements());
                result.setPageIndex((int)p.getPage());
                result.setPageSize((int)p.getSize());

                result.setContentArray(
                    p.getItems()
                        .stream()
                        .map(n -> contentMapper.asContent(n))
                        .toArray(Content[]::new)
                );

                return result;
            });
        } catch (Exception e) {
            throw new it.doqui.index.ecmengine.mtom.exception.SystemException(e);
        }
    }

    @Deprecated
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public int getTotalResultsLucene(SearchParams lucene, MtomOperationContext context) throws InvalidParameterException, SearchException, InvalidCredentialsException, EcmEngineTransactionException {
        lucene.setLimit(1);
        lucene.setPageSize(0);
        lucene.setPageIndex(0);

        return call(context, () -> {
            try {
                var s = prepareSearch(lucene);
                var p = searchService.findNodes(StringUtils.stripToEmpty(lucene.getLuceneQuery()), s.getLeft(), s.getRight());
                return (int)p.getTotalElements();
            } catch (BadQueryException | SearchEngineException e) {
                throw new SearchException(e.getMessage(), e);
            } catch (IOException e) {
                throw new EcmEngineTransactionException(e.getMessage());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node createContent(Node parent, Content content, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        return createRichContent(parent, content, null, false, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node createRichContent(Node parent, Content content, AclRecord[] acls, boolean inherits, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(parent);
        validate(() -> {
            Objects.requireNonNull(content, "Content node must not be null");
            Objects.requireNonNull(content.getTypePrefixedName(), "Content type must not be null");
            Objects.requireNonNull(content.getPrefixedName(), "Prefixed name must not be null");

            if (acls != null) {
                for (AclRecord acl : acls) {
                    Objects.requireNonNull(acl, "ACL record must not be null");
                    Objects.requireNonNull(StringUtils.stripToNull(acl.getAuthority()), "ACL authority is either null or empty");
                    Objects.requireNonNull(StringUtils.stripToNull(acl.getPermission()), "ACL permission is either null or empty");
                }
            }
        });

        return call(context, () -> {
            var input = contentMapper.asInputNodeRequest(content, LinkedInputNodeRequest.class);
            if (acls != null) {
                var acl =  new PermissionsDescriptor();
                acl.setInheritance(inherits);
                acl.getPermissions().addAll(aclMapper.asList(acls));
                input.setPermissionsDescriptor(acl);
            }

            fillContentStream(input, content);
            input.getAssociations().stream().findFirst().ifPresent(p -> p.setVertexUUID(parent.getUid()));
            log.trace("Creating content {}", input);

            try {
                var uuid = nodeService.createNode(input);
                return new Node(uuid);
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage(), e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node[] massiveCreateContent(
        Node[] parents,
        Content[] contents,
        MassiveParameter massiveParameter,
        MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        final List<LinkedInputNodeRequest> inputs = new LinkedList<>();
        validate(() -> {
            Objects.requireNonNull(parents, "Parents must not be null");
            Objects.requireNonNull(contents, "Contents must not be null");
            if (parents.length != contents.length) {
                throw new InvalidParameterException(
                    String.format("Parents and Contents must have the same size. #nodes = %d, #contents = %d", parents.length, contents.length));
            }

            for (int i=0; i<parents.length; i++) {
                Node parent = parents[i];
                Content content = contents[i];
                Objects.requireNonNull(parent, String.format("Parent[%d] must not be null", i));
                Objects.requireNonNull(parent.getUid(), String.format("Parent[%d] UUID must not be null", i));
                Objects.requireNonNull(content, String.format("Content[%d] must not be null", i));
                Objects.requireNonNull(content.getTypePrefixedName(), String.format("Content[%d] type must not be null", i));
                Objects.requireNonNull(content.getPrefixedName(), String.format("Content[%d] prefixed name must not be null", i));

                // crea oggetto mappato
                LinkedInputNodeRequest input = contentMapper.asInputNodeRequest(content, LinkedInputNodeRequest.class);
                input.getAssociations().stream().findFirst().ifPresent(p -> p.setVertexUUID(parent.getUid()));
                fillContentStream(input, content);
                inputs.add(input);
            }
        });

        return call(context, () -> {
            try {
                List<String> uuids = nodeService.createNodes(inputs);
                return uuids.stream().map(Node::new).toArray(Node[]::new);
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage(), e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node createContentFromTemporaney(
        Node parentNode,
        Content content,
        MtomOperationContext context,
        Node tempNode)
        throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(parentNode);

        validate(() -> {
            Objects.requireNonNull(tempNode, "Temp node must not be null");
            Objects.requireNonNull(tempNode.getUid(), "Temp node UUID must not be null");
        });

        validate(() -> {
            Objects.requireNonNull(content, "Content node must not be null");
            Objects.requireNonNull(content.getTypePrefixedName(), "Content type must not be null");
        });

        return call(context, () -> {
            var ref = new ContentRef();
            ref.setTenant(temporaryService.getTemporaryTenant());
            ref.setUuid(tempNode.getUid());

            var cd = new ExternalContentDescriptor();
            cd.setName(Optional.ofNullable(content.getContentPropertyPrefixedName()).orElse(CM_CONTENT));
            cd.setMimetype(content.getMimeType());
            cd.setEncoding(content.getEncoding());

            var source = new ExternalContentDescriptor.ExternalSource();
            source.setRef(ref);
            cd.setSource(source);

            var input = contentMapper.asInputNodeRequest(content, LinkedInputNodeRequest.class);
            input.getAssociations().stream().findFirst().ifPresent(p -> p.setVertexUUID(parentNode.getUid()));
            input.getProperties().put(cd.getName(), cd);
            log.trace("Creating content {} from temp {}", input, tempNode.getUid());

            try {
                var uuid = nodeService.createNode(input);
                return new Node(uuid);
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage(), e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void updateMetadata(Node node, Content newContent, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(node);
        validate(() -> Objects.requireNonNull(newContent, "Content node must not be null"));

        call(context, () -> {
            var n = contentMapper.asInputIdentifiedNodeRequest(newContent);
            nodeService.updateNode(node.getUid(), n, Set.of(HANDLE_CONTENT_PROPERTIES));
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void massiveUpdateMetadata(Node[] nodes, Content[] newContents, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(nodes);
        validate(() -> {
            Objects.requireNonNull(newContents, "Contents must not be null");
            if (nodes.length != newContents.length) {
                throw new InvalidParameterException(
                    String.format("Nodes and Contents must have the same size. #nodes = %d, #newContents = %d", nodes.length, newContents.length));
            }
        });

        call(context, () -> {
            List<InputIdentifiedNodeRequest> inputs = new LinkedList<>();
            for (int i = 0; i < nodes.length; i++) {
                InputIdentifiedNodeRequest n = contentMapper.asInputIdentifiedNodeRequest(newContents[i]);
                n.setUuid(nodes[i].getUid());
                inputs.add(n);
            }

            nodeService.updateNodes(inputs, Set.of(HANDLE_CONTENT_PROPERTIES));
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void deleteNode(Node node, int mode, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(node);
        DeleteMode deleteMode = mapDeleteMode(mode);
        call(context, () -> {
            nodeService.deleteNode(node.getUid(), deleteMode);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void deleteContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        deleteNode(node, 0, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void massiveDeleteNode(Node[] nodes, int mode, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(nodes);
        DeleteMode deleteMode = mapDeleteMode(mode);
        call(context, () -> {
            List<NodeOperation> operations = new ArrayList<>();
            Arrays.stream(nodes)
                .map(Node::getUid)
                .forEach(n -> {
                    NodeOperation o = new NodeOperation();
                    o.setOp(NodeOperation.NodeOperationType.DELETE);
                    o.setUuid(n);
                    o.setOperand(new NodeOperation.DeleteOperand().setMode(deleteMode));
                    operations.add(o);
                });
            multipleNodeOperationService.performOperations(operations, null, OperationMode.SYNC);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void massiveDeleteContent(Node[] nodes, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        massiveDeleteNode(nodes, 0, context);
    }

    private DeleteMode mapDeleteMode(int mode) {
        var wrapper = new Object(){ DeleteMode deleteMode = DeleteMode.DELETE; };
        validate(() -> {
            switch (mode) {
                case 0:
                    wrapper.deleteMode = DeleteMode.DELETE;
                    break;
                case 1:
                    wrapper.deleteMode = DeleteMode.PURGE;
                    break;
                case 2:
                    wrapper.deleteMode = DeleteMode.PURGE_COMPLETE;
                    break;
                default:
                    throw new InvalidParameterException("Invalid mode value " + mode);
            }
        });
        return wrapper.deleteMode;
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node restoreContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, EcmEngineException {
        validate(node);
        return call(context, () -> {
            archiveService.restoreNode(node.getUid(), null, LinkMode.ALL);
            return node;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node copyNode(Node source, Node parent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return copyNodeCopyChildren(source, parent, false, null, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node copyNodeCopyChildren(Node source, Node parent, boolean copyChildren, Association newAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(source);
        validate(parent);

        return call(context, () -> {
            var association = new LinkItemRequest();
            association.setVertexUUID(parent.getUid());
            if (newAssociation != null) {
                if (newAssociation.getPrefixedName() != null) {
                    association.setName(newAssociation.getPrefixedName());
                } else if (newAssociation.getTargetPrefixedName() != null) {
                    association.setName(newAssociation.getTargetPrefixedName());
                }

                if (newAssociation.getTypePrefixedName() != null) {
                    association.setTypeName(newAssociation.getTypePrefixedName());
                }
            }

            return new Node(nodeService.copyNode(source.getUid(), association, copyChildren, true, CopyMode.NAME));
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node copyNodeCloneContent(Node source, Node parent, boolean copyChildren, Association newAssociation, String uidContentNames, MtomOperationContext context)
        throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return copyNodeCopyChildren(source, parent, copyChildren, newAssociation, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void massiveRemoveAspects(Node[] nodes, Aspect[] aspectsToRemove, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(nodes);
        var input = new InputNodeRequest();
        validate(() -> {
            Objects.requireNonNull(aspectsToRemove, "No aspect to remove specified");
            for (int i=0; i<aspectsToRemove.length; i++) {
                Objects.requireNonNull(aspectsToRemove[i], String.format("Aspect[%d] must not be null", i));
                Objects.requireNonNull(aspectsToRemove[i].getPrefixedName(), String.format("Aspect[%d] name must not be null", i));
                input.getAspectOperations().put(aspectsToRemove[i].getPrefixedName(), InputNodeRequest.AspectOperation.REMOVE);
            }
        });

        massiveUpdate(nodes, input, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void massiveRemoveProperties(Node[] nodes, Property[] propertiesToRemove, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(nodes);
        var input = new InputNodeRequest();
        validate(() -> {
            Objects.requireNonNull(propertiesToRemove, "No property to remove specified");
            for (int i=0; i<propertiesToRemove.length; i++) {
                Objects.requireNonNull(propertiesToRemove[i], String.format("Property[%d] must not be null", i));
                Objects.requireNonNull(propertiesToRemove[i].getPrefixedName(), String.format("Property[%d] name must not be null", i));
                input.getProperties().put(propertiesToRemove[i].getPrefixedName(), null);
            }
        });

        massiveUpdate(nodes, input, context);
    }

    private void massiveUpdate(Node[] nodes, InputNodeRequest input, MtomOperationContext context) {
        call(context, () -> {
            List<InputIdentifiedNodeRequest> inputs = new LinkedList<>();
            for (Node node : nodes) {
                var n = new InputIdentifiedNodeRequest();
                n.copy(input);
                n.setUuid(node.getUid());
                inputs.add(n);
            }

            nodeService.updateNodes(inputs, Set.of(HANDLE_CONTENT_PROPERTIES));
            return null;
        });
    }

    private void fillContentStream(LinkedInputNodeRequest input, Content content) {
        if (content.getContent() != null) {
            var cs = new ContentStream();
            cs.setMimetype(content.getMimeType());
            cs.setEncoding(content.getEncoding());
            cs.setName(Optional.ofNullable(content.getContentPropertyPrefixedName()).orElse(CM_CONTENT));
            cs.setInputStream(new ByteArrayInputStream(content.getContent()));
            input.getProperties().put(cs.getName(), cs);
        }
    }
}
