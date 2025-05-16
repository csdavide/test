package it.doqui.libra.librabl.api.v1.cxf.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.business.service.interfaces.SharedLinkService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.security.PkRequest;
import it.doqui.libra.librabl.views.share.KeyRequest;
import it.doqui.libra.librabl.views.share.SharingRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static it.doqui.libra.librabl.utils.RSAUtils.verify;

@ApplicationScoped
@Slf4j
public class SharedLinksServiceBridge extends AbstractServiceBridge {

    @Inject
    SharedLinkService sharedLinkService;

    @Inject
    ObjectMapper objectMapper;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String[] getPublicKeys(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, ReadException {
        return call(context, () -> authenticationService.listPublicKeys()
            .stream()
            .filter(pk -> pk.getScopes().contains("shared-link"))
            .map(PkItem::getKey)
            .toList()
            .toArray(new String[0]));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public void addPublicKey(String publicKey, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, InsertException,
        EcmEngineTransactionException {
        call(context, () -> {
            var request = new PkRequest();
            request.setKey(publicKey);
            request.setUsername("admin");
            request.getScopes().add("shared-link");
            try {
                authenticationService.addPublicKey(request);
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage());
            }

            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void removePublicKey(String publicKey, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException, DeleteException,
        EcmEngineTransactionException {
        call(context, () -> {
            authenticationService.deletePublicKey(publicKey);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public DocumentPath getAbsolutePathFromSharedLink(SharedLinkInfo sharedLinkInfo)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException {
        validate(() -> {
            requireNonNull(sharedLinkInfo, "SharedLinkInfo");
            requireNonNull(sharedLinkInfo.getRequestUrl(), "SharedLinkInfo requestUrl");
            requireNonNull(sharedLinkInfo.getSharedLink(), "SharedLinkInfo sharedLink");
        });

        return call(() -> {
            var a = sharedLinkService.streamSharedContentData(sharedLinkInfo.getRequestUrl(), sharedLinkInfo.getSharedLink());
            return map(a);
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public DocumentPath getAbsolutePathFromKeyPayload(KeyPayloadDto payload)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException {

        validate(() -> {
            requireNonNull(payload, "KeyPayload");
            requireNonNull(payload.getRequest(), "KeyPayload request");
            requireNonNull(payload.getRequest().getTenantName(), "KeyPayload request tenant name");
            requireNonNull(payload.getRequest().getPublicKey(), "KeyPayload request public key");
            requireNonNull(payload.getSignature(), "KeyPayload signature");
        });

        try {
            var plainText = objectMapper.writeValueAsString(payload.getRequest());
            log.debug("Verifying request '{}'", plainText);
            if (!verify(plainText, payload.getSignature(), payload.getRequest().getPublicKey())) {
                throw new BadRequestException("Invalid signature");
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException(e.getMessage());
        } catch (SystemException e) {
            log.error(e.getMessage(), e);
            throw new PermissionDeniedException(e.getMessage());
        }

        return call(() -> {
            var r = payload.getRequest();
            var request = new KeyRequest();
            request.setPublicKey(r.getPublicKey());
            request.setTenant(r.getTenantName());
            request.setUuid(r.getUid());
            request.setContentPropertyName(r.getContentPropertyPrefixedName());
            request.setValidUntil(r.getValidUntil());

            return map(sharedLinkService.streamSharedContentData(request));
        });
    }

    private DocumentPath map(NodeAttachment a) {
        var result = new DocumentPath();
        result.setMimetype(a.getContentProperty().getMimetype());
        result.setContentLength(a.getContentProperty().getSize());
        result.setFileName(a.getName());
        result.setPath(a.getFile().getAbsolutePath());
        return result;
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public String shareDocument(Node document, SharingInfo sharingInfo, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(document);
        validate(() -> {
            requireNonNull(sharingInfo, "SharingInfo");
            requireNonNull(sharingInfo.getContentPropertyPrefixedName(), "SharingInfo contentPropertyPrefixedName");
            requireNonNull(sharingInfo.getSource(), "SharingInfo source");
        });
        return call(context, () -> sharedLinkService.shareNodeContent(document.getUid(), map(sharingInfo)));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void updateSharedLink(Node document, SharingInfo sharingInfo, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(document);
        validate(() -> {
            requireNonNull(sharingInfo, "SharingInfo");
            requireNonNull(sharingInfo.getSharedLink(), "SharingInfo sharedLink");
        });
        call(context, () -> {
            sharedLinkService.updateSharedLink(document.getUid(), sharingInfo.getSharedLink(), map(sharingInfo));
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void removeSharedLink(Node document, SharingInfo sharingInfo, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(document);
        validate(() -> {
            requireNonNull(sharingInfo, "SharingInfo");
            requireNonNull(sharingInfo.getSharedLink(), "SharingInfo sharedLink");
        });
        call(context, () -> {
            sharedLinkService.removeSharedLink(document.getUid(), sharingInfo.getSharedLink());
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public SharingInfo[] getSharingInfos(Node document, MtomOperationContext context) throws InvalidParameterException,
        NoSuchNodeException, PermissionDeniedException, ReadException, InvalidCredentialsException {
        validate(document);
        return call(context, () -> {
           var items = sharedLinkService.listSharingItems(document.getUid());
            return items.stream()
                .map(item -> {
                    var info = new SharingInfo();
                    info.setSharedLink(item.getUrl());
                    info.setSource(item.getSource());
                    info.setFromDate(item.getFromDate() == null ? null : item.getFromDate().format(DateISO8601Utils.dateFormat));
                    info.setToDate(item.getToDate() == null ? null : item.getToDate().format(DateISO8601Utils.dateFormat));
                    info.setContentPropertyPrefixedName(item.getContentPropertyName());
                    info.setResultPropertyPrefixedName(item.getFilePropertyName());
                    info.setResultContentDisposition(item.getDisposition());
                    return info;
                })
                .toList()
                .toArray(new SharingInfo[0]);
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void retainDocument(Node document, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(document);
        call(context, () -> {
            sharedLinkService.removeAllSharedLinks(document.getUid());
            return null;
        });
    }

    private SharingRequest map(SharingInfo sharingInfo) {
        var sharingRequest = new SharingRequest();
        sharingRequest.setContentPropertyName(sharingInfo.getContentPropertyPrefixedName());
        sharingRequest.setSource(sharingInfo.getSource());
        sharingRequest.setFromDate(StringUtils.isBlank(sharingInfo.getFromDate()) ? null : DateISO8601Utils.parseAsZonedDateTime(sharingInfo.getFromDate()));
        sharingRequest.setToDate(StringUtils.isBlank(sharingInfo.getToDate()) ? null : DateISO8601Utils.parseAsZonedDateTime(sharingInfo.getToDate()));
        sharingRequest.setFilePropertyName(StringUtils.stripToNull(sharingInfo.getResultPropertyPrefixedName()));
        sharingRequest.setDisposition(StringUtils.stripToNull(sharingInfo.getResultContentDisposition()));

        return sharingRequest;
    }
}
