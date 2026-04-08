package it.doqui.libra.librabl.application.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import it.doqui.libra.librabl.application.model.share.KeyRequest;
import it.doqui.libra.librabl.application.model.share.SharingItem;
import it.doqui.libra.librabl.application.model.share.SharingRequest;
import it.doqui.libra.librabl.application.model.user.PkItem;
import it.doqui.libra.librabl.application.ports.in.SharedLinkUseCase;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.domain.service.MimeTypeService;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;

import static it.doqui.libra.librabl.domain.model.graph.Constants.ASPECT_ECMSYS_SHARED;
import static it.doqui.libra.librabl.domain.model.graph.Constants.PROP_ECMSYS_SHARED_LINKS;

@ApplicationScoped
@Slf4j
public class SharedLinkService implements SharedLinkUseCase {

    @ConfigProperty(name = "libra.sharedlinks.checkRequestUrlEnabled", defaultValue = "false")
    boolean checkRequestUrlEnabled;

    @ConfigProperty(name = "libra.files.hmac.secret")
    String secret;

    @Inject
    NodeManager nodeManager;

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Inject
    SimpleNodeAccessManager nodeAccessManager;

    @Inject
    AttachmentHelper contentRetriever;

    @Inject
    MimeTypeService mimeTypeService;

    @Inject
    SharedLinkHandler sharedLinkHandler;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Inject
    TenantRepository tenantRepository;

    @Override
    public Collection<PkItem> listPublicKeys() {
        if (!sessionContext.getUserContext().isAdmin()) {
            throw new ForbiddenException("Admin required");
        }

        return authenticationManagerPort.listPublicKeys()
            .stream()
            .filter(pk -> pk.getScopes().contains("shared-link"))
            .toList();
    }

    private NodeAttachment streamSignedContentData(String token) {
        try {
            var alg = Algorithm.HMAC256(secret);
            var verifier = JWT.require(alg).build();
            var jwt = verifier.verify(token);

            //noinspection OptionalAssignedToNull
            authenticationManagerPort.authenticateUser(AuthorityRef.valueOf(jwt.getSubject()), null, SessionMode.SYNC);
            return nodeManager.getNodeContent(new Vertex(VertexType.UUID, jwt.getClaim("uuid").asString()), new URI(jwt.getClaim("contentUrl").asString()));
        } catch (JWTVerificationException | IllegalArgumentException | URISyntaxException | IOException e) {
            throw new ForbiddenException();
        }
    }

    @Override
    public NodeAttachment streamSharedContentData(String requestUrl, String inputKey) {
        if (Strings.CS.startsWith(inputKey, "ey")) {
            return streamSignedContentData(inputKey);
        }

        var key = normalizeBase64(inputKey);
        var decodedKey = new String(Base64.getDecoder().decode(key)).split("\\|");
        if (decodedKey.length < 3) {
            throw new ForbiddenException();
        }

        log.trace("Got request url: {}", requestUrl);
        log.trace("Decoded key: {}", String.join("|", decodedKey));
        //cHJpbWFyeXxBQ1RBLk1JTEFOTy5DT1JSRU5URXxlOGI1MWE4NTI1NDQxMWVkOWU2Yjk5MmUwNTg3OGU3ZHwxNjYxNTIxNDUxMzY2fDk0OTk4NDA
        // ==> primary|ACTA.MILANO.CORRENTE|e8b51a85254411ed9e6b992e05878e7d|1661521451366|9499840
        // |intranet|acta-doc:contenuto|acta-doc:nomeFile||2022-08-26T15:44:11.247+02:00|2022-09-05T23:59:59.999+02:00
        var tenant = decodedKey[1];

        var uuid = decodedKey[2];
        uuid = uuid.substring(0, 8) + "-" + uuid.substring(8);
        uuid = uuid.substring(0, 13) + "-" + uuid.substring(13);
        uuid = uuid.substring(0, 18) + "-" + uuid.substring(18);
        uuid = uuid.substring(0, 23) + "-" + uuid.substring(23);

        //noinspection OptionalAssignedToNull
        authenticationManagerPort.authenticateUser(new AuthorityRef("admin", TenantRef.valueOf(tenant)), null, SessionMode.SYNC);
        var n = nodeManager.getNodeMetadata(new Vertex(VertexType.UUID, uuid), Set.of(MapOption.DEFAULT), null, null)
                .orElseThrow(NotFoundException::new);

        log.trace("Got node {}", n);
        if (n.isPublic() && Strings.CS.equals(decodedKey[0], "public")) {
            final String contentPropertyName = decodedKey.length > 5 ? decodedKey[5] : null;
            return findNodeAttachment(uuid, contentPropertyName, null, null);
        }

        if (!n.getAspects().contains(ASPECT_ECMSYS_SHARED)) {
            log.warn("Node {} does not contain ASPECT_ECMSYS_SHARED", n.getUuid());
            throw new ForbiddenException("Node not shared");
        }

        if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
            var now = ZonedDateTime.now();
            String finalUuid = uuid;
            return sharedLinks.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(s -> s.split("\\|"))
                .filter(a -> Strings.CS.equals(normalizeBase64(a[0]), key))
                .filter(a -> {
                    if (checkRequestUrlEnabled) {
                        var ok = ConfigProvider.getConfig()
                            .getOptionalValue("sharedlink." + StringUtils.stripToEmpty(a[1]), String.class)
                            .map(url -> {
                                var match = Strings.CS.startsWith(requestUrl, url);
                                if (!match) {
                                    log.error("Invalid url {}: expected an url starting with {} ", requestUrl, url);
                                }
                                return match;
                            })
                            .orElse(true);

                        if (!ok) {
                            throw new ForbiddenException("Invalid url");
                        }
                    }

                    return true;
                })
                .filter(a -> {
                    if (a.length > 5 && StringUtils.isNotBlank(a[5])) {
                        var d = DateISO8601Utils.parseAsZonedDateTime(a[5]);
                        var valid = d == null || d.isBefore(now);
                        if (!valid) {
                            throw new WebException(426, "Link not yet valid");
                        }
                    }

                    return true;
                })
                .filter(a -> {
                    if (a.length > 6 && StringUtils.isNotBlank(a[6])) {
                        var d = DateISO8601Utils.parseAsZonedDateTime(a[6]);
                        var valid = d == null || d.isAfter(now);
                        if (!valid) {
                            throw new WebException(426, "Link expired");
                        }
                    }

                    return true;
                })
                .map(a -> new SharedLinkInfo(ArrayUtils.get(a, 1), ArrayUtils.get(a, 2), ArrayUtils.get(a, 3), ArrayUtils.get(a, 4)))
                .findFirst()
                .map(sharedLink -> {
                    String resultFilename = null;
                    if (StringUtils.isNotBlank(sharedLink.filePropertyName) && !Strings.CI.equals(sharedLink.filePropertyName, "null")) {
                        resultFilename = Optional.ofNullable(n.getProperties().get(sharedLink.filePropertyName)).map(Object::toString).orElse(null);
                    }
                    return findNodeAttachment(finalUuid, sharedLink.contentPropertyName, resultFilename, sharedLink.disposition);
                })
                .orElseThrow(() -> {
                    log.warn("Unable to find shared link for node {} and key {}", finalUuid, key);
                    return new ForbiddenException();
                });
        }

        log.warn("No link found for key {}", key);
        throw new ForbiddenException();
    }

    @Override
    public NodeAttachment streamSharedContentData(KeyRequest request) {
        authenticationManagerPort.authenticateUsingPK(TenantRef.valueOf(request.getTenant()), request.getPublicKey(), List.of("shared-link"));
        return findNodeAttachment(request.getUuid(), request.getContentPropertyName(), null, null);
    }

    @Override
    public String shareNodeContent(String uuid, SharingRequest sharingRequest) {
        return transactionManagerPort.perform(() -> {
            var encodedKey = sharedLinkHandler.createEncodedKey(uuid, "primary", null);
            var sharingStruct = getSharingStruct(uuid, encodedKey, sharingRequest);
            var n = sharingStruct.n;
            var urlPrefix = sharingStruct.urlPrefix;
            var entry = sharingStruct.entry;

            final List<String> targetLinks;
            if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
                targetLinks = processLinks(n, sharedLinks, encodedKey, entry, () -> { throw new BadRequestException("Key already present"); }, null);
                sharedLinks.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(s -> s.split("\\|"))
                    .filter(a -> Strings.CS.equals(a[0], encodedKey))
                    .findFirst()
                    .ifPresent(x -> {
                        throw new BadRequestException("Key already present");
                    });

                targetLinks.add(entry);
            } else {
                targetLinks = List.of(entry);
            }

            n.getProperties().put(PROP_ECMSYS_SHARED_LINKS, targetLinks);
            boolean indexingRequired = n.getAspects().add(ASPECT_ECMSYS_SHARED);
            nodeAccessManager.updateNodeWithIndexing(n, indexingRequired);
            return UriBuilder.fromUri(urlPrefix).path(encodedKey).build().toString();
        });
    }

    @Override
    public void updateSharedLink(String uuid, String key, SharingRequest sharingRequest) {
        transactionManagerPort.perform(() -> {
            var sharingStruct = getSharingStruct(uuid, key, sharingRequest);
            var n = sharingStruct.n;
            var entry = sharingStruct.entry;

            if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
                var targetLinks = processLinks(n, sharedLinks, key, entry, null, () -> { throw new PreconditionFailedException("Key not available"); });
                n.getProperties().put(PROP_ECMSYS_SHARED_LINKS, targetLinks);
                nodeAccessManager.updateNodeWithIndexing(n, false);
            } else {
                throw new PreconditionFailedException("Key not available");
            }

            return null;
        });
    }

    @Override
    public void removeSharedLink(String uuid, String key) {
        transactionManagerPort.perform(() -> {
            if (key == null) {
                throw new BadRequestException("No key provided");
            }

            var n = nodeAccessManager.getNode(uuid, PermissionFlag.W);
            boolean indexingRequired = false;
            if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
                var targetLinks = processLinks(n, sharedLinks, key, null, null, () -> { throw new PreconditionFailedException("Key not available"); });
                if (targetLinks.isEmpty()) {
                    n.getProperties().remove(PROP_ECMSYS_SHARED_LINKS);
                    n.getAspects().remove(ASPECT_ECMSYS_SHARED);
                    indexingRequired = true;
                } else {
                    n.getProperties().put(PROP_ECMSYS_SHARED_LINKS, targetLinks);
                }
            } else {
                throw new PreconditionFailedException("Key not available");
            }

            nodeAccessManager.updateNodeWithIndexing(n, indexingRequired);
            return null;
        });
    }

    @Override
    public void removeAllSharedLinks(String uuid) {
        transactionManagerPort.perform(tx -> {
            var n = nodeAccessManager.getNode(uuid, PermissionFlag.W);
            if (!n.getAspects().contains(ASPECT_ECMSYS_SHARED)) {
                throw new PreconditionFailedException("Node not shared");
            }

            n.getAspects().remove(ASPECT_ECMSYS_SHARED);
            n.getProperties().remove(PROP_ECMSYS_SHARED_LINKS);
            nodeAccessManager.updateNode(tx, n);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public Collection<SharingItem> listSharingItems(String uuid) {
        var n = nodeAccessManager.getNode(uuid, PermissionFlag.R);
        return sharedLinkHandler.listSharingItems(n);
    }

    private List<String> processLinks(ActiveNode n, Collection<?> sharedLinks, String key, String entry, Supplier<Void> ifFound, Supplier<Void> ifNotFound) {
        var targetLinks = new ArrayList<String>();
        var found = false;

        var tenantData = tenantRepository.findByIdOptional(sessionContext.getTenant()).map(TenantSpace::getData).orElse(new TenantData());
        final ZonedDateTime removableTime = tenantData.getLinkGracePeriodSeconds() > 0
            ? ZonedDateTime.now().minusSeconds(tenantData.getLinkGracePeriodSeconds())
            : null;

        var prefix = key.substring(key.lastIndexOf("/") + 1) + "|";
        for (Object o : sharedLinks) {
            var s = o.toString();
            if (Strings.CS.startsWith(s, prefix)) {
                found = true;
                if (entry != null) {
                    targetLinks.add(entry.substring(entry.lastIndexOf("/") + 1));
                }
            } else {
                var item = sharedLinkHandler.asSharingItem(s);
                if (item != null) {
                    if (item.getToDate() != null && removableTime != null && item.getToDate().isBefore(removableTime)) {
                        log.debug("Removing expired shared link {} from uuid {} ({}): expired at {} removable because older than {}",
                            item.getKey(), n.getUuid(), n.getTenant(), item.getToDate(), removableTime);
                    } else {
                        targetLinks.add(s);
                    }
                }
            }
        }

        if (!found) {
            if (ifNotFound != null) {
                ifFound.get();
            }
        } else {
            if (ifFound != null) {
                ifFound.get();
            }
        }

        return targetLinks;
    }

    private NodeAttachment findNodeAttachment(String uuid, String contentPropertyName, String targetFileName, String disposition) {
        try {
            var a = nodeManager.getNodeContent(new Vertex(VertexType.UUID, uuid), ContentReferenceable.of(contentPropertyName));
            assert a != null;
            var mimeType = a.getContentProperty().getMimetype();
            var fileName = mimeTypeService.filename(Optional.ofNullable(targetFileName).orElse(a.getName()), mimeType);
            return NodeAttachment.builder()
                .name(fileName)
                .contentProperty(a.getContentProperty())
                .file(a.getFile())
                .disposition(Optional.ofNullable(disposition).orElse(a.getDisposition()))
                .opaque(a.isOpaque())
                .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String normalizeBase64(String key) {
        var d = new StringBuilder(key.replace("-", "+").replace("_", "/"));
        while (d.length() % 4 != 0) {
            d.append("=");
        }
        return d.toString();
    }

    private SharedLinkStruct getSharingStruct(String uuid, String key, SharingRequest sharingRequest) {
        var n = nodeAccessManager.getNode(uuid, PermissionFlag.W);
        final String contentPropertyName;
        try {
            var fd = n.getContent(ContentReferenceable.of(Optional.ofNullable(sharingRequest).map(SharingRequest::getContentPropertyName).orElse(null)))
                    .orElseThrow(PreconditionFailedException::new);
            var a = contentRetriever.attachment(n, fd);
            contentPropertyName = a.getContentProperty().getName();
        } catch (IOException e) {
            throw new SystemException(e);
        }

        var source = StringUtils.stripToEmpty(Optional.ofNullable(sharingRequest).map(SharingRequest::getSource).orElse("internet"));
        var urlPrefix = ConfigProvider.getConfig()
            .getOptionalValue("sharedlink." + source, String.class)
            .orElseThrow(() -> new BadRequestException("Invalid source: " + source));

        sharingRequest = Optional.ofNullable(sharingRequest).orElse(new SharingRequest());
        if (sharingRequest.getToDate() == null) {
            var tenantData = tenantRepository.findByIdOptional(sessionContext.getTenant()).map(TenantSpace::getData).orElse(new TenantData());
            if (tenantData.getLinkDefaultDurationSeconds() > 0) {
                sharingRequest.setToDate(ZonedDateTime.now().plusSeconds(tenantData.getLinkDefaultDurationSeconds()));
            }
        }

        log.debug("Generating entry for key {} cp {} for sharingRequest {}", key, contentPropertyName, sharingRequest);
        var entry = String.join("|", List.of(
            key,
            source,
            contentPropertyName,
            StringUtils.stripToEmpty(sharingRequest.getFilePropertyName()), // resultPropertyPrefixedName
            StringUtils.stripToEmpty(sharingRequest.getDisposition()), // resultContentDisposition
            sharingRequest.getFromDate() == null ? "" : sharingRequest.getFromDate().format(DateISO8601Utils.dateFormat),
            sharingRequest.getToDate() == null ? "" : sharingRequest.getToDate().format(DateISO8601Utils.dateFormat)
        ));

        return new SharedLinkStruct(n, urlPrefix, entry);
    }

    private record SharedLinkInfo(String env, String contentPropertyName, String filePropertyName, String disposition) {
    }

    private record SharedLinkStruct(ActiveNode n, String urlPrefix, String entry) {}
}
