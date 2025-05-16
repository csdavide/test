package it.doqui.index.ecmengine.mtom.impl;

import it.doqui.index.ecmengine.mtom.ServiceImpl;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.cxf.impl.ServiceDispatcher;
import it.doqui.libra.librabl.api.v1.rest.dto.ReindexParameters;
import jakarta.activation.DataHandler;
import jakarta.inject.Inject;
import jakarta.jws.*;
import lombok.extern.slf4j.Slf4j;

import java.rmi.RemoteException;

@WebService(
    endpointInterface = "it.doqui.index.ecmengine.mtom.ServiceImpl",
    serviceName = "StreamingService",
    targetNamespace = "http://server.mtom.ecmengine.index.doqui.it/",
    portName = "ServiceImplPort"
)
@Slf4j
public class StreamingServiceImpl implements ServiceImpl {

    @Inject
    ServiceDispatcher serviceDispatcher;

    public String echo(@WebParam(header=true) int hdValue, int bdValue) {
			return hdValue + "_" + bdValue;
		}

    @Override
    public AsyncSigillo getAsyncSigillo(String tokenUid, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().getAsyncSigillo(tokenUid, context);
    }

    @Override
    public SigilloSignedExt sigilloSignatureExt(Document document, SigilloSigner params, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().sigilloSignatureExt(document, params, context);
    }

    @Override
    public TransactionInfo getTxnInfoFromDbid(long dbid, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getTxnInfoFromDbid(dbid, context);
    }

    @Override
    public TransactionInfo getTxnInfoFromUID(Node node, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getTxnInfoFromUID(node, context);
    }

    @Override
    public long getDbIdFromUID(Node node, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, ReadException, RemoteException {
        return serviceDispatcher.getProxy().getDbIdFromUID(node, context);
    }

    @Override
    public Job getServiceJobInfo(String jobName, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, ReadException, RemoteException {
        return serviceDispatcher.getProxy().getServiceJobInfo(jobName, context);
    }

    @Override
    public void updateUserMetadata(User utente, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchUserException, UserUpdateException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().updateUserMetadata(utente, context);
    }

    @Override
    public void updateUserPassword(User utente, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchUserException, UserUpdateException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().updateUserPassword(utente, context);
    }

    @Override
    public void deployCustomModel(CustomModel model, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        serviceDispatcher.getProxy().deployCustomModel(model, context);
    }

    @Override
    public void undeployCustomModel(CustomModel model, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        serviceDispatcher.getProxy().undeployCustomModel(model, context);
    }

    @Override
    public CustomModel[] getAllCustomModels(MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getAllCustomModels(context);
    }

    @Override
    public Tenant[] getAllTenants(MtomOperationContext context) throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, NoDataExtractedException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getAllTenants(context);
    }

    @Override
    public String getSignatureType(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, ReadException, RemoteException {
        return serviceDispatcher.getProxy().getSignatureType(node, content, context);
    }

    @Override
    public Node copyContentBetweenTenant(Node source, Node parentDestination, MtomOperationContext sourceContext, MtomOperationContext destinationContext) throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, EcmEngineTransactionException, ReadException, InvalidCredentialsException, InsertException, RemoteException {
        return serviceDispatcher.getProxy().copyContentBetweenTenant(source, parentDestination, sourceContext, destinationContext);
    }

    @Override
    public FileFormatInfo[] identifyDocument(Document document, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, RemoteException, EcmEngineException {
        return serviceDispatcher.getProxy().identifyDocument(document, context);
    }

    @Override
    public void revertVersion(Node node, String versionLabel, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, EcmEngineTransactionException, PermissionDeniedException, EcmEngineException, RemoteException {
        serviceDispatcher.getProxy().revertVersion(node, versionLabel, context);
    }

    @Override
    public void updateAcl(Node node, AclRecord[] acls, MtomOperationContext context) throws AclEditException, NoSuchNodeException, RemoteException, InvalidParameterException, InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        serviceDispatcher.getProxy().updateAcl(node, acls, context);
    }

    @Override
    public SearchResponse listDeletedNodes(NodeArchiveParams params, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().listDeletedNodes(params, context);
    }

    @Override
    public NodeResponse listDeletedNodesNoMetadata(NodeArchiveParams params, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, PermissionDeniedException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().listDeletedNodesNoMetadata(params, context);
    }

    @Override
    public String getSignatureTypeData(byte[] data, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, RemoteException {
        return serviceDispatcher.getProxy().getSignatureTypeData(data, context);
    }

    @Override
    public String directUploadMethod(Attachment myFile, String usr, String pwd, String repo, String node, String prefixedName) throws SystemException {
        return serviceDispatcher.getProxy().directUploadMethod(myFile, usr, pwd, repo, node, prefixedName);
    }

    @Override
    public String uploadMethod(Attachment myFile, String usr, String pwd, String repo, String parent) throws SystemException {
        return serviceDispatcher.getProxy().uploadMethod(myFile, usr, pwd, repo, parent);
    }

    @Override
    public Attachment downloadMethod(String uid, String usr, String pwd, String repo, String prefixedName) throws SystemException {
        return serviceDispatcher.getProxy().downloadMethod(uid, usr, pwd, repo, prefixedName);
    }

    @Override
    public DataHandler generateRendition(RenditionData inRenditionData, boolean toBeExtracted, String usr, String pwd, String repo) throws SystemException {
        return serviceDispatcher.getProxy().generateRendition(inRenditionData, toBeExtracted, usr, pwd, repo);
    }

    @Override
    public RenditionDocument generateRenditionContent(Content xml, RenditionTransformer renditionTransformer, MtomOperationContext context) throws InvalidParameterException, RemoteException {
        return serviceDispatcher.getProxy().generateRenditionContent(xml, renditionTransformer, context);
    }

    @Override
    public byte[] generateRenditionRenditionDocument(RenditionDocument renditionDocument, RenditionTransformer renditionTransformer, MtomOperationContext context) throws NoSuchNodeException, InvalidParameterException, PermissionDeniedException, TransformException, RemoteException {
        return serviceDispatcher.getProxy().generateRenditionRenditionDocument(renditionDocument, renditionTransformer, context);
    }

    @Override
    public SearchResponse luceneSearch(SearchParams lucene, MtomOperationContext context) throws SystemException {
        return serviceDispatcher.getProxy().luceneSearch(lucene, context);
    }

    @Override
    public NodeResponse luceneSearchNoMetadata(SearchParams lucene, MtomOperationContext context) throws InvalidParameterException, TooManyResultsException, SearchException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().luceneSearchNoMetadata(lucene, context);
    }

    @Override
    public void addAcl(Node node, AclRecord[] acls, MtomOperationContext context) throws AclEditException, NoSuchNodeException, RemoteException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        serviceDispatcher.getProxy().addAcl(node, acls, context);
    }

    @Override
    public DigestInfo generateDigestFromContent(Content content, DigestInfo digestInfo, MtomOperationContext context) throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException, RemoteException {
        return serviceDispatcher.getProxy().generateDigestFromContent(content, digestInfo, context);
    }

    @Override
    public DigestInfo generateDigestFromUID(Node node, NodeInfo nodeInfo, DigestInfo digestInfo, MtomOperationContext context) throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException, NoSuchNodeException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().generateDigestFromUID(node, nodeInfo, digestInfo, context);
    }

    @Override
    public VerifyReport verifyDocument(EnvelopedContent envelopedContent, MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyDocument(envelopedContent, context);
    }

    @Override
    public VerifyReport verifyDocumentVerifyParameter(EnvelopedContent envelopedContent, VerifyParameter verifyParameter, MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyDocumentVerifyParameter(envelopedContent, verifyParameter, context);
    }

    @Override
    public VerifyReport verifyDocumentNode(Node[] node, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyDocumentNode(node, context);
    }

    @Override
    public VerifyReport verifyDocumentNodeVerifyParameter(Node[] node, VerifyParameter verifyParameter, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyDocumentNodeVerifyParameter(node, verifyParameter, context);
    }

    @Override
    public VerifyReport verifySignedDocument(Document document, Document detachedSignature, VerifyParameter verifyParameter, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifySignedDocument(document, detachedSignature, verifyParameter, context);
    }

    @Override
    public VerifyReport xmlVerify(Node node, NodeInfo nodeInfo, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().xmlVerify(node, nodeInfo, context);
    }

    @Override
    public Document extractDocumentFromEnvelope(EnvelopedContent envelopedContent, MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().extractDocumentFromEnvelope(envelopedContent, context);
    }

    @Override
    public Document extractDocumentFromEnvelopeNode(Node node, MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().extractDocumentFromEnvelopeNode(node, context);
    }

    @Override
    public Document extractDocumentFromEnvelopeEnvelopedDocument(Document envelopedDocument, MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().extractDocumentFromEnvelopeEnvelopedDocument(envelopedDocument, context);
    }

    @Override
    public void changeAcl(Node node, AclRecord[] acls, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, AclEditException, NoSuchNodeException, EcmEngineTransactionException, PermissionDeniedException, RemoteException {
        serviceDispatcher.getProxy().changeAcl(node, acls, context);
    }

    @Override
    public AclRecord[] listAcl(Node node, AclListParams params, MtomOperationContext context) throws NoSuchNodeException, RemoteException, AclEditException, InvalidParameterException, InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        return serviceDispatcher.getProxy().listAcl(node, params, context);
    }

    @Override
    public String createUser(User nuovoUtente, MtomOperationContext context) throws UserCreateException, RemoteException, UserAlreadyExistsException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        return serviceDispatcher.getProxy().createUser(nuovoUtente, context);
    }

    @Override
    public void removeAcl(Node node, AclRecord[] acls, MtomOperationContext context) throws AclEditException, NoSuchNodeException, RemoteException, InvalidParameterException, InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        serviceDispatcher.getProxy().removeAcl(node, acls, context);
    }

    @Override
    public void resetAcl(Node node, AclRecord filter, MtomOperationContext context) throws InvalidParameterException, AclEditException, NoSuchNodeException, RemoteException, InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        serviceDispatcher.getProxy().resetAcl(node, filter, context);
    }

    @Override
    public void setInheritsAcl(Node node, boolean inherits, MtomOperationContext context) throws NoSuchNodeException, RemoteException, AclEditException, InvalidParameterException, InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        serviceDispatcher.getProxy().setInheritsAcl(node, inherits, context);
    }

    @Override
    public Node createContent(Node parent, Content content, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().createContent(parent, content, context);
    }

    @Override
    public Node createRichContent(Node parent, Content content, AclRecord[] acls, boolean inherits, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().createRichContent(parent, content, acls, inherits, context);
    }

    @Override
    public Node addRenditionTransformer(Node nodoXml, RenditionTransformer renditionTransformer, String propertyContent, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().addRenditionTransformer(nodoXml, renditionTransformer, propertyContent, context);
    }

    @Override
    public Node cancelCheckOutContent(Node node, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, CheckInCheckOutException, RemoteException, EcmEngineTransactionException, PermissionDeniedException {
        return serviceDispatcher.getProxy().cancelCheckOutContent(node, context);
    }

    @Override
    public Node getWorkingCopy(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, EcmEngineException, RemoteException, InvalidCredentialsException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getWorkingCopy(node, context);
    }

    @Override
    public Node checkInContent(Node workingCopy, MtomOperationContext context) throws InvalidParameterException, CheckInCheckOutException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().checkInContent(workingCopy, context);
    }

    @Override
    public Node checkOutContent(Node node, MtomOperationContext context) throws InvalidParameterException, CheckInCheckOutException, NoSuchNodeException, RemoteException, InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        return serviceDispatcher.getProxy().checkOutContent(node, context);
    }

    @Override
    public void deleteContent(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException, PermissionDeniedException, RemoteException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().deleteContent(node, context);
    }

    @Override
    public void deleteNode(Node node, int mode, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException, PermissionDeniedException, RemoteException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().deleteNode(node, mode, context);
    }

    @Override
    public void deleteRenditionTransformer(Node xml, Node renditionTransformer, MtomOperationContext context) throws InvalidParameterException, DeleteException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().deleteRenditionTransformer(xml, renditionTransformer, context);
    }

    @Override
    public Content getContentMetadata(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, EcmEngineTransactionException, ReadException, InvalidCredentialsException, RemoteException {
        return serviceDispatcher.getProxy().getContentMetadata(node, context);
    }

    @Override
    public void purgeContent(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException, PermissionDeniedException, RemoteException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().purgeContent(node, context);
    }

    @Override
    public void purgeNode(Node node, boolean remove, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException, PermissionDeniedException, RemoteException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().purgeNode(node, remove, context);
    }

    @Override
    public Node restoreContent(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, RemoteException, EcmEngineTransactionException, EcmEngineException {
        return serviceDispatcher.getProxy().restoreContent(node, context);
    }

    @Override
    public Path[] getPaths(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, SearchException, RemoteException, InvalidCredentialsException {
        return serviceDispatcher.getProxy().getPaths(node, context);
    }

    @Override
    public int getTotalResultsLucene(SearchParams lucene, MtomOperationContext context) throws InvalidParameterException, SearchException, InvalidCredentialsException, RemoteException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getTotalResultsLucene(lucene, context);
    }

    @Override
    public RenditionTransformer getRenditionTransformer(Node nodoTransformer, String propertyContent, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getRenditionTransformer(nodoTransformer, propertyContent, context);
    }

    @Override
    public RenditionDocument getRendition(Node nodoTransformer, String propertyContent, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getRendition(nodoTransformer, propertyContent, context);
    }

    @Override
    public RenditionDocument[] getNodeRenditions(Node nodoxml, Node nodoTransformer, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getNodeRenditions(nodoxml, nodoTransformer, context);
    }

    @Override
    public RenditionTransformer[] getRenditionTransformers(Node xml, String propertyContent, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getRenditionTransformers(xml, propertyContent, context);
    }

    @Override
    public RenditionDocument[] getRenditions(Node xml, String propertyContent, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getRenditions(xml, propertyContent, context);
    }

    @Override
    public String getTypePrefixedName(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, InvalidCredentialsException, RemoteException {
        return serviceDispatcher.getProxy().getTypePrefixedName(node, context);
    }

    @Override
    public void moveNode(Node source, Node parent, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().moveNode(source, parent, context);
    }

    @Override
    public void renameContent(Node source, String nameValue, String propertyPrefixedName, boolean onlyPrimaryAssociation, MtomOperationContext context) throws InvalidParameterException, InsertException, UpdateException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().renameContent(source, nameValue, propertyPrefixedName, onlyPrimaryAssociation, context);
    }

    @Override
    public Path getAbsolutePath(Node node, NodeInfo nodeInfo, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, PermissionDeniedException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().getAbsolutePath(node, nodeInfo, context);
    }

    @Override
    public DocumentPath getAbsolutePathFromSharedLink(SharedLinkInfo sharedLinkInfo) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, PermissionDeniedException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().getAbsolutePathFromSharedLink(sharedLinkInfo);
    }

    @Override
    public DocumentPath getAbsolutePathFromKeyPayload(KeyPayloadDto keyPayload) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, PermissionDeniedException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().getAbsolutePathFromKeyPayload(keyPayload);
    }

    @Override
    public AssociationResponse getAssociations(Node node, AssociationsSearchParams associationsSearchParams, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, SearchException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getAssociations(node, associationsSearchParams, context);
    }

    @Override
    public Association[] getAssociationsAssocType(Node node, String assocType, int maxResults, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, SearchException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getAssociationsAssocType(node, assocType, maxResults, context);
    }

    @Override
    public FileFormatInfo[] getFileFormatInfo(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, RemoteException, EcmEngineException {
        return serviceDispatcher.getProxy().getFileFormatInfo(node, content, context);
    }

    @Override
    public FileFormatInfo[] getFileFormatInfoFileInfo(FileInfo fileInfo, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, RemoteException, EcmEngineException {
        return serviceDispatcher.getProxy().getFileFormatInfoFileInfo(fileInfo, context);
    }

    @Override
    public FileInfo getFileInfo(Node node, NodeInfo nodeInfo, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, PermissionDeniedException, SearchException, RemoteException {
        return serviceDispatcher.getProxy().getFileInfo(node, nodeInfo, context);
    }

    @Override
    public String getFileType(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, ReadException, RemoteException {
        return serviceDispatcher.getProxy().getFileType(node, content, context);
    }

    @Override
    public Mimetype[] getMimetype(Mimetype mimetype) throws InvalidParameterException, RemoteException {
        return serviceDispatcher.getProxy().getMimetype(mimetype);
    }

    @Override
    public byte[] retrieveContentData(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, ReadException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().retrieveContentData(node, content, context);
    }

    @Override
    public Node setRendition(Node nodoTransformer, RenditionDocument renditionDocument, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().setRendition(nodoTransformer, renditionDocument, context);
    }

    @Override
    public Node setNodeRendition(Node nodoxml, Node nodoTransformer, RenditionDocument renditionDocument, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().setNodeRendition(nodoxml, nodoTransformer, renditionDocument, context);
    }

    @Override
    public void updateContentData(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        serviceDispatcher.getProxy().updateContentData(node, content, context);
    }

    @Override
    public void updateMetadata(Node node, Content newContent, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().updateMetadata(node, newContent, context);
    }

    @Override
    public String shareDocument(Node document, SharingInfo sharingInfo, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().shareDocument(document, sharingInfo, context);
    }

    @Override
    public void updateSharedLink(Node document, SharingInfo sharingInfo, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().updateSharedLink(document, sharingInfo, context);
    }

    @Override
    public void removeSharedLink(Node document, SharingInfo sharingInfo, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().removeSharedLink(document, sharingInfo, context);
    }

    @Override
    public void retainDocument(Node document, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().retainDocument(document, context);
    }

    @Override
    public TimestampDocument callTimestamping(TimestampEnvelopedContent envelopedContent, MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().callTimestamping(envelopedContent, context);
    }

    @Override
    public boolean compareDigest(Node node, Node tempNode, NodeInfo nodeInfo, NodeInfo tempNodeInfo, DigestInfo digestInfo, MtomOperationContext context) throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException, NoSuchNodeException, SearchException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().compareDigest(node, tempNode, nodeInfo, tempNodeInfo, digestInfo, context);
    }

    @Override
    public Node createContentFromTemporaney(Node parentNode, Content content, MtomOperationContext context, Node tempNode) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().createContentFromTemporaney(parentNode, content, context, tempNode);
    }

    @Override
    public void unLinkContent(Node source, Node destination, Association association, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        serviceDispatcher.getProxy().unLinkContent(source, destination, association, context);
    }

    @Override
    public void linkContent(Node source, Node destination, Association association, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().linkContent(source, destination, association, context);
    }

    @Override
    public Job linkContentJob(Node source, Node destination, Association association, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().linkContentJob(source, destination, association, context);
    }

    @Override
    public Job moveNodeJob(Node source, Node parent, Association newAssociation, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().moveNodeJob(source, parent, newAssociation, context);
    }

    @Override
    public void massiveDeleteContent(Node[] nodes, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().massiveDeleteContent(nodes, context);
    }

    @Override
    public void massiveDeleteNode(Node[] nodes, int mode, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().massiveDeleteNode(nodes, mode, context);
    }

    @Override
    public Content[] massiveGetContentMetadata(Node[] nodes, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().massiveGetContentMetadata(nodes, context);
    }

    @Override
    public Content[] massiveGetContentMetadataPartial(Node[] nodes, Property[] properties, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().massiveGetContentMetadataPartial(nodes, properties, context);
    }

    @Override
    public void massiveRemoveAspects(Node[] nodes, Aspect[] aspectsToRemove, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().massiveRemoveAspects(nodes, aspectsToRemove, context);
    }

    @Override
    public void massiveRemoveProperties(Node[] nodes, Property[] propertiesToRemove, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().massiveRemoveProperties(nodes, propertiesToRemove, context);
    }

    @Override
    public ContentData[] massiveRetrieveContentData(Node[] nodes, Content[] contents, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().massiveRetrieveContentData(nodes, contents, context);
    }

    @Override
    public void massiveUpdateMetadata(Node[] nodes, Content[] newContents, MtomOperationContext context) throws InvalidParameterException, UpdateException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().massiveUpdateMetadata(nodes, newContents, context);
    }

    @Override
    public Node[] massiveCreateContent(Node[] parents, Content[] contents, MassiveParameter massiveParameter, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().massiveCreateContent(parents, contents, massiveParameter, context);
    }

    @Override
    public boolean isInheritsAcl(Node node, MtomOperationContext context) throws NoSuchNodeException, RemoteException, AclEditException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException {
        return serviceDispatcher.getProxy().isInheritsAcl(node, context);
    }

    @Override
    public User[] listUsers(Group gruppo, MtomOperationContext context) throws RemoteException, EcmEngineTransactionException, EcmEngineException, InvalidParameterException, InvalidCredentialsException, NoDataExtractedException {
        return serviceDispatcher.getProxy().listUsers(gruppo, context);
    }

    @Override
    public User[] listAllUsers(User filter, MtomOperationContext context) throws RemoteException, InvalidCredentialsException, NoDataExtractedException, InvalidParameterException, EcmEngineTransactionException, EcmEngineException {
        return serviceDispatcher.getProxy().listAllUsers(filter, context);
    }

    @Override
    public User[] listAllUserNames(User filter, MtomOperationContext context) throws RemoteException, InvalidCredentialsException, NoDataExtractedException, InvalidParameterException, EcmEngineTransactionException, EcmEngineException {
        return serviceDispatcher.getProxy().listAllUserNames(filter, context);
    }

    @Override
    public Group[] listAllGroups(Group filter, MtomOperationContext context) throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, EcmEngineException, NoDataExtractedException, RemoteException {
        return serviceDispatcher.getProxy().listAllGroups(filter, context);
    }

    @Override
    public void deleteUser(User utente, MtomOperationContext context) throws InvalidParameterException, UserDeleteException, NoSuchUserException, RemoteException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        serviceDispatcher.getProxy().deleteUser(utente, context);
    }

    @Override
    public void deleteGroup(Group gruppo, MtomOperationContext context) throws InvalidParameterException, GroupDeleteException, NoSuchGroupException, RemoteException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        serviceDispatcher.getProxy().deleteGroup(gruppo, context);
    }

    @Override
    public String createGroup(Group nuovoGruppo, Group gruppoPadre, MtomOperationContext context) throws GroupCreateException, RemoteException, GroupAlreadyExistsException, InvalidParameterException, NoSuchGroupException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        return serviceDispatcher.getProxy().createGroup(nuovoGruppo, gruppoPadre, context);
    }

    @Override
    public SystemInfo getSystemInfo(MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getSystemInfo(context);
    }

    @Override
    public void addUserToGroup(User utente, Group gruppo, MtomOperationContext context) throws GroupEditException, RemoteException, NoSuchUserException, NoSuchGroupException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        serviceDispatcher.getProxy().addUserToGroup(utente, gruppo, context);
    }

    @Override
    public void removeUserFromGroup(User utente, Group gruppo, MtomOperationContext context) throws GroupEditException, RemoteException, NoSuchUserException, NoSuchGroupException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        serviceDispatcher.getProxy().removeUserFromGroup(utente, gruppo, context);
    }

    @Override
    public Node copyNode(Node source, Node parent, MtomOperationContext context) throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().copyNode(source, parent, context);
    }

    @Override
    public Node copyNodeCopyChildren(Node source, Node parent, boolean copyChildren, Association newAssociation, MtomOperationContext context) throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().copyNodeCopyChildren(source, parent, copyChildren, newAssociation, context);
    }

    @Override
    public Node copyNodeCloneContent(Node source, Node parent, boolean copyChildren, Association newAssociation, String uidContentNames, MtomOperationContext context) throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().copyNodeCloneContent(source, parent, copyChildren, newAssociation, uidContentNames, context);
    }

    @Override
    public void moveNodeAssociation(Node source, Node parent, Association newAssociation, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().moveNodeAssociation(source, parent, newAssociation, context);
    }

    @Override
    public Content getVersionMetadata(Node node, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, EcmEngineTransactionException, ReadException, InvalidCredentialsException, RemoteException {
        return serviceDispatcher.getProxy().getVersionMetadata(node, context);
    }

    @Override
    public byte[] retrieveVersionContentData(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, ReadException, RemoteException, EcmEngineTransactionException, PermissionDeniedException {
        return serviceDispatcher.getProxy().retrieveVersionContentData(node, content, context);
    }

    @Override
    public Version getVersion(Node node, String versionLabel, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, EcmEngineTransactionException, EcmEngineException, InvalidCredentialsException, RemoteException, PermissionDeniedException {
        return serviceDispatcher.getProxy().getVersion(node, versionLabel, context);
    }

    @Override
    public Version[] getAllVersions(Node node, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, EcmEngineTransactionException, EcmEngineException, RemoteException, PermissionDeniedException {
        return serviceDispatcher.getProxy().getAllVersions(node, context);
    }

    @Override
    public void moveContentFromTemp(Node source, Node destination, Property contentProperty, MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().moveContentFromTemp(source, destination, contentProperty, context);
    }

    @Override
    public boolean testResources() throws EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().testResources();
    }

    @Override
    public String verifyAsyncDocument(Document document, Document detachedSignature, VerifyParameter verifyParameter, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyAsyncDocument(document, detachedSignature, verifyParameter, context);
    }

    @Override
    public AsyncReportDto getAsyncReport(String tokenUid) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().getAsyncReport(tokenUid);
    }

    @Override
    public int getSignaturesNumber(Document paramDocument1, Document paramDocument2, MtomOperationContext paramMtomOperationContext) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().getSignaturesNumber(paramDocument1, paramDocument2, paramMtomOperationContext);
    }

    @Override
    public VerifyReportExtDto verifyDocumentExt(Document document, Document detachedSignature, VerifyParameter verifyParameter, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyDocumentExt(document, detachedSignature, verifyParameter, context);
    }

    @Override
    public VerifyCertificateReport verifyCertificate(CertBuffer certBuffer, VerifyParameter verifyParameter, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, InsertException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().verifyCertificate(certBuffer, verifyParameter, context);
    }

    @Override
    public void addPublicKey(String publicKey, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, InsertException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().addPublicKey(publicKey, context);
    }

    @Override
    public void removePublicKey(String publicKey, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, DeleteException, EcmEngineTransactionException, RemoteException {
        serviceDispatcher.getProxy().removePublicKey(publicKey, context);
    }

    @Override
    public String[] getPublicKeys(MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, ReadException, RemoteException {
        return serviceDispatcher.getProxy().getPublicKeys(context);
    }

    @Override
    public SharingInfo[] getSharingInfos(Node document, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, ReadException, InvalidCredentialsException, RemoteException {
        return serviceDispatcher.getProxy().getSharingInfos(document, context);
    }

    @Override
    public ModelDescriptor[] getAllModelDescriptors(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getAllModelDescriptors(context);
    }

    @Override
    public ModelMetadata getModelDefinition(ModelDescriptor modelDescriptor, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoDataExtractedException, EcmEngineException, RemoteException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().getModelDefinition(modelDescriptor, context);
    }

    @Override
    public TypeMetadata getTypeDefinition(ModelDescriptor modelDescriptor, MtomOperationContext context)
        throws InvalidParameterException, NoDataExtractedException, EcmEngineException, EcmEngineTransactionException,
        InvalidCredentialsException, RemoteException {
        return serviceDispatcher.getProxy().getTypeDefinition(modelDescriptor, context);
    }

    @Override
    public Repository[] getRepositories(MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getRepositories(context);
    }

    @Override
    public boolean tenantExists(Tenant tenant, MtomOperationContext context) throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().tenantExists(tenant, context);
    }

    @Override
    public AsyncReportDto getAsyncReportExt(String tokenUid, MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return serviceDispatcher.getProxy().getAsyncReportExt(tokenUid, context);
    }

    @Override
    public Tenant[] getAllTenantNames(MtomOperationContext context) throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException, NoDataExtractedException, PermissionDeniedException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getAllTenantNames(context);
    }

    @Override
    public SearchResponse luceneSearchRetro(SearchParams lucene, MtomOperationContext context) throws InvalidParameterException, TooManyResultsException, SearchException, InvalidCredentialsException, PermissionDeniedException, RemoteException, EcmEngineTransactionException {
        return serviceDispatcher.getProxy().luceneSearchRetro(lucene, context);
    }

    @Override
    public FileReport getFileReport(Node node, Content content, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getFileReport(node, content, context);
    }

    @Override
    public FileReport getFileReportStream(FileInfo fileInfo, MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getFileReportStream(fileInfo, context);
    }

    @Override
    public String getJobError(String tokenUid, String type, MtomOperationContext context) throws InvalidParameterException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().getJobError(tokenUid, type, context);
    }

    @Override
    public boolean onlineReindex(ReindexParameters params, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        EcmEngineTransactionException, EcmEngineException, RemoteException {
        return serviceDispatcher.getProxy().onlineReindex(params, context);
    }

    @Override
    public void createTenant(Tenant tenant, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        EcmEngineTransactionException, EcmEngineException, RemoteException {
        serviceDispatcher.getProxy().createTenant(tenant, context);
    }
}
