package it.doqui.libra.librabl.api.v1.rest.components.interfaces;

import it.doqui.libra.librabl.api.v1.rest.dto.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface NodesBusinessInterface {

    // done
    Node getContentMetadata(String uid);

    // done
    PathInfo[] getPaths(String uid);

    // done
    GetAssociationsResponse getAssociations(String uid, String associationType, AssociationsSearchParams searchParams);

    @Deprecated
    PathInfo getAbsolutePath(String uid, String prefixedName);

    @Deprecated
    FileFormatInfo[] getFileFormatInfo(String uid, String contentPropertyName);

    // done
    String createContent(String parentNodeUid, Node node, byte[] bytes);

    // done
    LuceneSearchResponse luceneSearch(SearchParams searchParams, String metadataItems);

    // done
    File retrieveContentData(String uid, String contentPropertyName);

    // done
    void restoreContent(String uid);

    // done
    void deleteNode(String uid, DeleteNodeAction action);

    // done
    void updateMetadata(String uid, Node node);

    // done
    void updateContentData(String uid, String contentPropertyName, String mimeType, String encoding, byte[] bytes)
	   ;

    // done
    void modifyAssociation(String uid, ModifyAssociationRequest associationInfo);

    // done
    String modifyAssociationJob(String uid, ModifyAssociationJobRequest associationInfo)
	   ;

    // done
    void addAcl(String uid, AclRecord[] aclRecords);

    // done
    void changeAcl(String uid, AclRecord[] aclRecords);

    // done
    void updateAcl(String uid, AclRecord[] aclRecords);

    // done
    void removeAcl(String uid, AclRecord[] aclRecords);

    // done
    void resetAcl(String uid, AclRecord filter);

    // done
    AclRecord[] listAcl(String uid, Boolean showInherited);

    // done
    boolean isInheritsAcl(String uid);

    // done
    void setInheritsAcl(String uid, boolean booleanValue);

    // done
    void retainDocument(String uid);

    // done
    SharingInfo[] getSharingInfos(String uid);

    // done
    void removeSharedLink(String uid, String shareLinkId);

    // done
    void updateSharedLink(String uid, String sharedLinkId, SharingInfo sharingInfo);

    // done
    String shareDocument(String uid, SharingInfo sharingInfo);

    @Deprecated
    String getAbsolutePathFromSharedLink(String shareLinkId);

    // done
    Node[] massiveCreateNode(String[] parentNodeUids, Node[] nodesRequests, Map<Integer, byte[]> contents,
	    boolean oldImplementation, boolean synchronousReindex);

    // done
    File massiveRetrieveContentData(List<Node> nodes) throws IOException;

    // done
    void massiveDeleteNode(List<String> uids, MassiveDeleteNodeAction action);

    // done
    Node[] massiveGetContentMetadata(List<String> uids);

    // done
    void massiveUpdateMetadata(List<Node> nodes);

    String copyContentBetweenTenant(String uid, DestinationInfo destinationInfo);

    // done
    String extractFromEnvelope(String uid, String contentPropertyName);

    // done
    FileFormatInfo[] identifyDocument(String uid, String contentPropertyName, Boolean store)
	   ;

    // done
    LuceneSearchResponse listDeletedNodes(NodeArchiveParams nodeArchiveParams, Boolean metadata)
	   ;

    void revertVersion(String uid, String versionName);

    // done
    String getSignatureType(String uid, String contentPropertyName, EncryptionInfo encryptionInfo)
	   ;

    String cancelCheckOutContent(String uid);

    String checkInContent(String uid);

    String checkOutContent(String uid);

    NodeVersion[] getAllVersions(String uid);

    NodeVersion getVersion(String uid, String versionName);

    Node getVersionMetadata(String uid);

    String getWorkingCopy(String uid);

    File retrieveVersionContentData(String uid, String contentPropertyName);

    // done
    String generateDigestFromUID(String uid, String contentPropertyName, String algorithm, Boolean enveloped)
	   ;

    // done
    boolean compareDigest(CompareDigestRequest compareDigestRequest, String algorithm);

    // done
    long getDbIdFromUID(String uid);

    // done
    String createContentFromTemp(String parentNodeUid, String tempNodeUid, Node node);

    void moveContentFromTemp(String sourceUid, String destinationUid, String contentPropertyName)
	   ;

    // done
    void renameContent(String uid, RenameContentRequest renameParams);

    String uploadContent(String parentUid, String prefixedName, InputStream data, String contentName,
	    String contentType);
}