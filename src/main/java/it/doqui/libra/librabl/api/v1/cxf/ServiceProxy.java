package it.doqui.libra.librabl.api.v1.cxf;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.rest.dto.ReindexParameters;
import jakarta.activation.DataHandler;
import jakarta.jws.WebParam;

import java.rmi.RemoteException;

public interface ServiceProxy {

    String echo(@WebParam(header = true) int hdValue, int bdValue);

    AsyncSigillo getAsyncSigillo(String tokenUid, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    SigilloSignedExt sigilloSignatureExt(Document document, SigilloSigner params, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    TransactionInfo getTxnInfoFromDbid(long dbid, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException;

    TransactionInfo getTxnInfoFromUID(Node node, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException;

    long getDbIdFromUID(Node node, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, ReadException, RemoteException;

    Job getServiceJobInfo(String jobName, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, ReadException, RemoteException;

    void updateUserMetadata(User utente, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchUserException, UserUpdateException, EcmEngineTransactionException, RemoteException;

    void updateUserPassword(User utente, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchUserException, UserUpdateException, EcmEngineTransactionException, RemoteException;

    void deployCustomModel(CustomModel model, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException;

    void undeployCustomModel(CustomModel model, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException;

    CustomModel[] getAllCustomModels(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException;

    Tenant[] getAllTenants(MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        NoDataExtractedException, PermissionDeniedException, EcmEngineException, RemoteException;

    String getSignatureType(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, ReadException, RemoteException;

    Node copyContentBetweenTenant(Node source, Node parentDestination, MtomOperationContext sourceContext,
                                  MtomOperationContext destinationContext)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException,
        EcmEngineTransactionException, ReadException, InvalidCredentialsException, InsertException, RemoteException;

    FileFormatInfo[] identifyDocument(Document document, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, RemoteException, EcmEngineException;

    void revertVersion(Node node, String versionLabel, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        EcmEngineTransactionException, PermissionDeniedException, EcmEngineException, RemoteException;

    void updateAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws AclEditException, NoSuchNodeException, RemoteException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException;

    SearchResponse listDeletedNodes(NodeArchiveParams params, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, SearchException, RemoteException;

    NodeResponse listDeletedNodesNoMetadata(NodeArchiveParams params, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException, RemoteException;

    String getSignatureTypeData(byte[] data, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, RemoteException;

    String directUploadMethod(@WebParam(name = "myFile") Attachment myFile, String usr, String pwd, String repo,
                              String node, String prefixedName) throws SystemException;

    String uploadMethod(@WebParam(name = "myFile") Attachment myFile, String usr, String pwd, String repo,
                        String parent) throws SystemException;

    Attachment downloadMethod(String uid, String usr, String pwd, String repo, String prefixedName)
        throws SystemException;

    DataHandler generateRendition(RenditionData inRenditionData, boolean toBeExtracted, String usr, String pwd,
                                  String repo) throws SystemException;

    RenditionDocument generateRenditionContent(Content xml, RenditionTransformer renditionTransformer,
                                               MtomOperationContext context) throws InvalidParameterException, RemoteException;

    byte[] generateRenditionRenditionDocument(RenditionDocument renditionDocument,
                                              RenditionTransformer renditionTransformer, MtomOperationContext context) throws NoSuchNodeException,
        InvalidParameterException, PermissionDeniedException, TransformException, RemoteException;

    SearchResponse luceneSearch(SearchParams lucene, MtomOperationContext context) throws SystemException;

    NodeResponse luceneSearchNoMetadata(SearchParams lucene, MtomOperationContext context)
        throws InvalidParameterException, TooManyResultsException, SearchException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    void addAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws AclEditException, NoSuchNodeException, RemoteException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    DigestInfo generateDigestFromContent(Content content, DigestInfo digestInfo, MtomOperationContext context)
        throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException,
        RemoteException;

    DigestInfo generateDigestFromUID(Node node, NodeInfo nodeInfo, DigestInfo digestInfo,
                                     MtomOperationContext context) throws InvalidCredentialsException, InvalidParameterException,
        PermissionDeniedException, ReadException, NoSuchNodeException, SearchException, RemoteException;

    VerifyReport verifyDocument(EnvelopedContent envelopedContent, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    VerifyReport verifyDocumentVerifyParameter(EnvelopedContent envelopedContent,
                                               VerifyParameter verifyParameter, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    VerifyReport verifyDocumentNode(Node[] node, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, EcmEngineTransactionException, RemoteException;

    VerifyReport verifyDocumentNodeVerifyParameter(Node[] node, VerifyParameter verifyParameter,
                                                   MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException;

    VerifyReport verifySignedDocument(Document document, Document detachedSignature,
                                      VerifyParameter verifyParameter, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, EcmEngineTransactionException, RemoteException;

    VerifyReport xmlVerify(Node node, NodeInfo nodeInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, EcmEngineTransactionException, RemoteException;

    Document extractDocumentFromEnvelope(EnvelopedContent envelopedContent, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Document extractDocumentFromEnvelopeNode(Node node, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Document extractDocumentFromEnvelopeEnvelopedDocument(Document envelopedDocument,
                                                          MtomOperationContext context) throws InsertException, NoSuchNodeException, InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void changeAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, AclEditException, NoSuchNodeException,
        EcmEngineTransactionException, PermissionDeniedException, RemoteException;

    AclRecord[] listAcl(Node node, AclListParams params, MtomOperationContext context)
        throws NoSuchNodeException, RemoteException, AclEditException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException;

    String createUser(User nuovoUtente, MtomOperationContext context)
        throws UserCreateException, RemoteException, UserAlreadyExistsException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    void removeAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws AclEditException, NoSuchNodeException, RemoteException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException;

    void resetAcl(Node node, AclRecord filter, MtomOperationContext context)
        throws InvalidParameterException, AclEditException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException;

    void setInheritsAcl(Node node, boolean inherits, MtomOperationContext context)
        throws NoSuchNodeException, RemoteException, AclEditException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException;

    Node createContent(Node parent, Content content, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Node createRichContent(Node parent, Content content, AclRecord[] acls, boolean inherits,
                           MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Node addRenditionTransformer(Node nodoXml, RenditionTransformer renditionTransformer, String propertyContent,
                                 MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Node cancelCheckOutContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        CheckInCheckOutException, RemoteException, EcmEngineTransactionException, PermissionDeniedException;

    Node getWorkingCopy(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, EcmEngineException, RemoteException,
        InvalidCredentialsException, EcmEngineTransactionException;

    Node checkInContent(Node workingCopy, MtomOperationContext context)
        throws InvalidParameterException, CheckInCheckOutException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Node checkOutContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, CheckInCheckOutException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException;

    void deleteContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, RemoteException, EcmEngineTransactionException;

    void deleteNode(Node node, int mode, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, RemoteException, EcmEngineTransactionException;

    void deleteRenditionTransformer(Node xml, Node renditionTransformer, MtomOperationContext context)
        throws InvalidParameterException, DeleteException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Content getContentMetadata(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException,
        EcmEngineTransactionException, ReadException, InvalidCredentialsException, RemoteException;

    void purgeContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, RemoteException, EcmEngineTransactionException;

    void purgeNode(Node node, boolean remove, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, RemoteException, EcmEngineTransactionException;

    Node restoreContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, RemoteException, EcmEngineTransactionException, EcmEngineException;

    Path[] getPaths(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException, SearchException,
        RemoteException, InvalidCredentialsException;

    int getTotalResultsLucene(SearchParams lucene, MtomOperationContext context)
        throws InvalidParameterException, SearchException, InvalidCredentialsException, RemoteException,
        EcmEngineTransactionException;

    RenditionTransformer getRenditionTransformer(Node nodoTransformer, String propertyContent,
                                                 MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    RenditionDocument getRendition(Node nodoTransformer, String propertyContent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    RenditionDocument[] getNodeRenditions(Node nodoxml, Node nodoTransformer, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    RenditionTransformer[] getRenditionTransformers(Node xml, String propertyContent,
                                                    MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    RenditionDocument[] getRenditions(Node xml, String propertyContent, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, RemoteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException;

    String getTypePrefixedName(Node node, MtomOperationContext context) throws InvalidParameterException,
        NoSuchNodeException, PermissionDeniedException, InvalidCredentialsException, RemoteException;

    void moveNode(Node source, Node parent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void renameContent(Node source, String nameValue, String propertyPrefixedName,
                       boolean onlyPrimaryAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Path getAbsolutePath(Node node, NodeInfo nodeInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException, RemoteException;

    DocumentPath getAbsolutePathFromSharedLink(SharedLinkInfo sharedLinkInfo)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException, RemoteException;

    DocumentPath getAbsolutePathFromKeyPayload(KeyPayloadDto keyPayload)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException, RemoteException;

    AssociationResponse getAssociations(Node node, AssociationsSearchParams associationsSearchParams,
                                        MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, SearchException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Association[] getAssociationsAssocType(Node node, String assocType, int maxResults,
                                           MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException, SearchException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    FileFormatInfo[] getFileFormatInfo(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, RemoteException, EcmEngineException;

    FileFormatInfo[] getFileFormatInfoFileInfo(FileInfo fileInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, RemoteException, EcmEngineException;

    FileInfo getFileInfo(Node node, NodeInfo nodeInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException, RemoteException;

    String getFileType(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, ReadException, RemoteException;

    Mimetype[] getMimetype(Mimetype mimetype) throws InvalidParameterException, RemoteException;

    byte[] retrieveContentData(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Node setRendition(Node nodoTransformer, RenditionDocument renditionDocument, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    Node setNodeRendition(Node nodoxml, Node nodoTransformer, RenditionDocument renditionDocument, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    void updateContentData(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    void updateMetadata(Node node, Content newContent, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    String shareDocument(Node document, SharingInfo sharingInfo, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    void updateSharedLink(Node document, SharingInfo sharingInfo, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    void removeSharedLink(Node document, SharingInfo sharingInfo, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    void retainDocument(Node document, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    TimestampDocument callTimestamping(TimestampEnvelopedContent envelopedContent, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    boolean compareDigest(Node node, Node tempNode, NodeInfo nodeInfo, NodeInfo tempNodeInfo,
                          DigestInfo digestInfo, MtomOperationContext context)
        throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException,
        NoSuchNodeException, SearchException, EcmEngineTransactionException, RemoteException;

    Node createContentFromTemporaney(Node parentNode, Content content, MtomOperationContext context,
                                     Node tempNode) throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void unLinkContent(Node source, Node destination, Association association, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException;

    void linkContent(Node source, Node destination, Association association, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Job linkContentJob(Node source, Node destination, Association association, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Job moveNodeJob(Node source, Node parent, Association newAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void massiveDeleteContent(Node[] nodes, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void massiveDeleteNode(Node[] nodes, int mode, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Content[] massiveGetContentMetadata(Node[] nodes, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Content[] massiveGetContentMetadataPartial(Node[] nodes, Property[] properties, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void massiveRemoveAspects(Node[] nodes, Aspect[] aspectsToRemove, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void massiveRemoveProperties(Node[] nodes, Property[] propertiesToRemove, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    ContentData[] massiveRetrieveContentData(Node[] nodes, Content[] contents, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void massiveUpdateMetadata(Node[] nodes, Content[] newContents, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Node[] massiveCreateContent(Node[] parents, Content[] contents, MassiveParameter massiveParameter,
                                MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    boolean isInheritsAcl(Node node, MtomOperationContext context) throws NoSuchNodeException, RemoteException,
        AclEditException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException;

    User[] listUsers(Group gruppo, MtomOperationContext context)
        throws RemoteException, EcmEngineTransactionException, EcmEngineException, InvalidParameterException,
        InvalidCredentialsException, NoDataExtractedException;

    User[] listAllUsers(User filter, MtomOperationContext context)
        throws RemoteException, InvalidCredentialsException, NoDataExtractedException, InvalidParameterException,
        EcmEngineTransactionException, EcmEngineException;

    User[] listAllUserNames(User filter, MtomOperationContext context)
        throws RemoteException, InvalidCredentialsException, NoDataExtractedException, InvalidParameterException,
        EcmEngineTransactionException, EcmEngineException;

    Group[] listAllGroups(Group filter, MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        EcmEngineException, NoDataExtractedException, RemoteException;

    void deleteUser(User utente, MtomOperationContext context)
        throws InvalidParameterException, UserDeleteException, NoSuchUserException, RemoteException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    void deleteGroup(Group gruppo, MtomOperationContext context)
        throws InvalidParameterException, GroupDeleteException, NoSuchGroupException, RemoteException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    String createGroup(Group nuovoGruppo, Group gruppoPadre, MtomOperationContext context)
        throws GroupCreateException, RemoteException, GroupAlreadyExistsException, InvalidParameterException,
        NoSuchGroupException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    SystemInfo getSystemInfo(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException, RemoteException;

    void addUserToGroup(User utente, Group gruppo, MtomOperationContext context) throws GroupEditException,
        RemoteException, NoSuchUserException, NoSuchGroupException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    void removeUserFromGroup(User utente, Group gruppo, MtomOperationContext context) throws GroupEditException,
        RemoteException, NoSuchUserException, NoSuchGroupException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException;

    // management
    Node copyNode(Node source, Node parent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Node copyNodeCopyChildren(Node source, Node parent, boolean copyChildren, Association newAssociation,
                              MtomOperationContext context)
        throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Node copyNodeCloneContent(Node source, Node parent, boolean copyChildren, Association newAssociation, String uidContentNames,
                              MtomOperationContext context)     throws InvalidParameterException, InsertException, CopyException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    void moveNodeAssociation(Node source, Node parent, Association newAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Content getVersionMetadata(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, PermissionDeniedException,
        EcmEngineTransactionException, ReadException, InvalidCredentialsException, RemoteException;

    byte[] retrieveVersionContentData(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException, ReadException,
        RemoteException, EcmEngineTransactionException, PermissionDeniedException;

    Version getVersion(Node node, String versionLabel, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, EcmEngineTransactionException, EcmEngineException,
        InvalidCredentialsException, RemoteException, PermissionDeniedException;

    Version[] getAllVersions(Node node, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        EcmEngineTransactionException, EcmEngineException, RemoteException, PermissionDeniedException;

    // security
    void moveContentFromTemp(Node source, Node destination, Property contentProperty,
                             MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    // massive
    boolean testResources() throws EcmEngineException, RemoteException;

    String verifyAsyncDocument(Document document, Document detachedSignature, VerifyParameter verifyParameter,
                               MtomOperationContext context) throws InvalidParameterException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    AsyncReportDto getAsyncReport(String tokenUid) throws InvalidParameterException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    int getSignaturesNumber(Document paramDocument1, Document paramDocument2, MtomOperationContext paramMtomOperationContext)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException, RemoteException;

    VerifyReportExtDto verifyDocumentExt(Document document, Document detachedSignature,
                                         VerifyParameter verifyParameter, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    VerifyCertificateReport verifyCertificate(CertBuffer certBuffer, VerifyParameter verifyParameter,
                                              MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, InsertException, EcmEngineTransactionException, RemoteException;

    void addPublicKey(String publicKey, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, InsertException,
        EcmEngineTransactionException, RemoteException;

    void removePublicKey(String publicKey, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, DeleteException,
        EcmEngineTransactionException, RemoteException;

    String[] getPublicKeys(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, ReadException, RemoteException;

    SharingInfo[] getSharingInfos(Node document, MtomOperationContext context) throws InvalidParameterException,
        NoSuchNodeException, PermissionDeniedException, ReadException, InvalidCredentialsException, RemoteException;

    ModelDescriptor[] getAllModelDescriptors(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, EcmEngineException, RemoteException;

    ModelMetadata getModelDefinition(ModelDescriptor modelDescriptor, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoDataExtractedException, EcmEngineException,
        RemoteException, EcmEngineTransactionException;

    TypeMetadata getTypeDefinition(ModelDescriptor modelDescriptor, MtomOperationContext context)
        throws InvalidParameterException, NoDataExtractedException, EcmEngineException, EcmEngineTransactionException,
        InvalidCredentialsException, RemoteException;

    Repository[] getRepositories(MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException;

    boolean tenantExists(Tenant tenant, MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineException, RemoteException;

    AsyncReportDto getAsyncReportExt(String tokenUid, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException;

    Tenant[] getAllTenantNames(MtomOperationContext context) throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        NoDataExtractedException, PermissionDeniedException, EcmEngineException, RemoteException;

    SearchResponse luceneSearchRetro(SearchParams lucene, MtomOperationContext context)
        throws InvalidParameterException, TooManyResultsException, SearchException, InvalidCredentialsException,
        PermissionDeniedException, RemoteException, EcmEngineTransactionException;

    FileReport getFileReport(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException;

    FileReport getFileReportStream(FileInfo fileInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException;

    String getJobError(String tokenUid, String type, MtomOperationContext context)
        throws InvalidParameterException, EcmEngineException, RemoteException;

    boolean onlineReindex(ReindexParameters params, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        EcmEngineTransactionException, EcmEngineException, RemoteException;

    void createTenant(Tenant tenant, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        EcmEngineTransactionException, EcmEngineException, RemoteException;
}
