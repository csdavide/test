package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.service.auth.AuthenticationService;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.SharedLinkService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.share.KeyRequest;
import it.doqui.libra.librabl.views.share.SharingItem;
import it.doqui.libra.librabl.views.share.SharingRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.ASPECT_ECMSYS_SHARED;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.PROP_ECMSYS_SHARED_LINKS;

@ApplicationScoped
@Slf4j
public class SharedLinkServiceImpl implements SharedLinkService {

    @ConfigProperty(name = "libra.sharedlinks.checkRequestUrlEnabled", defaultValue = "false")
    boolean checkRequestUrlEnabled;

    @Inject
    TransactionService txManager;

    @Inject
    NodeManager nodeManager;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    SimpleNodeAccessManager nodeAccessManager;

    @Inject
    ContentRetriever contentRetriever;

    private final SecureRandom random = new SecureRandom();

    @Override
    public Collection<PkItem> listPublicKeys() {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin required");
        }

        return authenticationService.listPublicKeys()
            .stream()
            .filter(pk -> pk.getScopes().contains("shared-link"))
            .toList();
    }

    @Override
    public NodeAttachment streamSharedContentData(String requestUrl, String inputKey) {
        var key = normalizeBase64(inputKey);
        var decodedKey = new String(Base64.getDecoder().decode(key)).split("\\|");
        if (decodedKey.length < 3) {
            throw new ForbiddenException();
        }

        log.debug("Got request url: {}", requestUrl);
        log.debug("Decoded key: {}", String.join("|", decodedKey));
        //cHJpbWFyeXxBQ1RBLk1JTEFOTy5DT1JSRU5URXxlOGI1MWE4NTI1NDQxMWVkOWU2Yjk5MmUwNTg3OGU3ZHwxNjYxNTIxNDUxMzY2fDk0OTk4NDA
        // |intranet|acta-doc:contenuto|acta-doc:nomeFile||2022-08-26T15:44:11.247+02:00|2022-09-05T23:59:59.999+02:00
        var tenant = decodedKey[1];

        var uuid = decodedKey[2];
        uuid = uuid.substring(0, 8) + "-" + uuid.substring(8);
        uuid = uuid.substring(0, 13) + "-" + uuid.substring(13);
        uuid = uuid.substring(0, 18) + "-" + uuid.substring(18);
        uuid = uuid.substring(0, 23) + "-" + uuid.substring(23);

        //noinspection OptionalAssignedToNull
        authenticationService.authenticateUser(new AuthorityRef("admin", TenantRef.valueOf(tenant)), null, UserContext.Mode.SYNC);
        var n = nodeManager.getNodeMetadata(uuid, Set.of(MapOption.DEFAULT), null, null)
                .orElseThrow(NotFoundException::new);

        log.trace("Got node {}", n);
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
                .filter(a -> StringUtils.equals(normalizeBase64(a[0]), key))
                .filter(a -> {
                    if (checkRequestUrlEnabled) {
                        var ok = ConfigProvider.getConfig()
                            .getOptionalValue("sharedlink." + StringUtils.stripToEmpty(a[1]), String.class)
                            .map(url -> {
                                var match = StringUtils.startsWith(requestUrl, url);
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
                    if (StringUtils.isNotBlank(sharedLink.filePropertyName) && !StringUtils.equalsIgnoreCase(sharedLink.filePropertyName, "null")) {
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
        authenticationService.authenticateUsingPK(TenantRef.valueOf(request.getTenant()), request.getPublicKey(), List.of("shared-link"));
        return findNodeAttachment(request.getUuid(), request.getContentPropertyName(), null, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String shareNodeContent(String uuid, SharingRequest sharingRequest) {
        return txManager.perform(tx -> {
            var encodedKey = encodeBase64(String.join("|", List.of(
                "primary",
                UserContextManager.getContext().getTenantRef().toString(),
                uuid.replace("-", ""),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(random.nextInt(10000000)))));

            var sharingStruct = getSharingStruct(uuid, encodedKey, sharingRequest);
            var n = sharingStruct.n;
            var urlPrefix = sharingStruct.urlPrefix;
            var entry = sharingStruct.entry;

            if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
                sharedLinks.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(s -> s.split("\\|"))
                    .filter(a -> StringUtils.equals(a[0], encodedKey))
                    .findFirst()
                    .ifPresent(x -> {
                        throw new BadRequestException("Key already present");
                    });

                ((Collection<String>)sharedLinks).add(entry);
            } else {
                n.getProperties().put(PROP_ECMSYS_SHARED_LINKS, List.of(entry));
            }

            n.getAspects().add(ASPECT_ECMSYS_SHARED);
            nodeAccessManager.updateNode(tx, n);
            return PerformResult.<String>builder()
                .result(UriBuilder.fromUri(urlPrefix).path(encodedKey).build().toString())
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public void updateSharedLink(String uuid, String key, SharingRequest sharingRequest) {
        txManager.perform(tx -> {
            var sharingStruct = getSharingStruct(uuid, key, sharingRequest);
            var n = sharingStruct.n;
            var entry = sharingStruct.entry;

            if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
                var targetLinks = new ArrayList<String>();
                var found = false;
                for (Object o : sharedLinks) {
                    var s = o.toString();
                    if (StringUtils.startsWith(s, key.substring(key.lastIndexOf("/") + 1) + "|")) {
                        found = true;
                        targetLinks.add(entry.substring(entry.lastIndexOf("/") + 1));
                    } else {
                        targetLinks.add(s);
                    }
                }

                if (!found) {
                    throw new PreconditionFailedException("Key not available");
                }

                n.getProperties().put(PROP_ECMSYS_SHARED_LINKS, targetLinks);
                nodeAccessManager.updateNode(tx, n);
            } else {
                throw new PreconditionFailedException("Key not available");
            }

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public void removeSharedLink(String uuid, String key) {
        txManager.perform(tx -> {
            if (key == null) {
                throw new BadRequestException("No key provided");
            }

            var n = nodeAccessManager.getNode(uuid, PermissionFlag.W);
            if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
                var targetLinks = new ArrayList<String>();
                var found = false;
                for (Object o : sharedLinks) {
                    var s = o.toString();
                    if (StringUtils.startsWith(s, key.substring(key.lastIndexOf("/") + 1) + "|")) {
                        found = true;
                    } else {
                        targetLinks.add(s);
                    }
                }

                if (!found) {
                    throw new PreconditionFailedException("Key not available");
                }

                if (targetLinks.isEmpty()) {
                    n.getProperties().remove(PROP_ECMSYS_SHARED_LINKS);
                    n.getAspects().remove(ASPECT_ECMSYS_SHARED);
                } else {
                    n.getProperties().put(PROP_ECMSYS_SHARED_LINKS, targetLinks);
                }
            } else {
                throw new PreconditionFailedException("Key not available");
            }

            nodeAccessManager.updateNode(tx, n);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public void removeAllSharedLinks(String uuid) {
        txManager.perform(tx -> {
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
        if (n.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
            return sharedLinks.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(s -> s.split("\\|"))
                .map(a -> {
                    var key = a[0];
                    var item = new SharingItem();
                    item.setSource(StringUtils.stripToNull(ArrayUtils.get(a, 1)));
                    item.setContentPropertyName(ArrayUtils.get(a, 2));
                    item.setFilePropertyName(StringUtils.stripToNull(ArrayUtils.get(a, 3)));
                    item.setDisposition(StringUtils.stripToNull(ArrayUtils.get(a, 4)));
                    item.setFromDate(Optional.ofNullable(ArrayUtils.get(a, 5)).map(StringUtils::stripToNull).map(DateISO8601Utils::parseAsZonedDateTime).orElse(null));
                    item.setToDate(Optional.ofNullable(ArrayUtils.get(a, 6)).map(StringUtils::stripToNull).map(DateISO8601Utils::parseAsZonedDateTime).orElse(null));
                    item.setUrl(
                        ConfigProvider.getConfig()
                            .getOptionalValue("sharedlink." + StringUtils.stripToEmpty(item.getSource()), String.class)
                            .map(prefix -> UriBuilder.fromUri(prefix).path(key).build().toString())
                            .orElse(null)
                    );

                    return StringUtils.isBlank(item.getUrl()) ? null : item;
                })
                .filter(Objects::nonNull)
                .toList();
        }

        return List.of();
    }

    private NodeAttachment findNodeAttachment(String uuid, String contentPropertyName, String targetFileName, String disposition) {
        try {
            var a = nodeManager.getNodeContent(uuid, contentPropertyName, null);
            return NodeAttachment.builder()
                .name(Optional.ofNullable(targetFileName).orElse(a.getName()))
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

    private String encodeBase64(String key) {
        return Base64.getUrlEncoder()
            .encodeToString(key.getBytes(StandardCharsets.UTF_8))
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "");
    }

    private SharedLinkStruct getSharingStruct(String uuid, String key, SharingRequest sharingRequest) {
        var n = nodeAccessManager.getNode(uuid, PermissionFlag.W);
        final String contentPropertyName;
        try {
            var a = contentRetriever.retrieveContent(
                n.getData(),
                Optional.ofNullable(sharingRequest).map(SharingRequest::getContentPropertyName).orElse(null),
                null);
            contentPropertyName = a.getContentProperty().getName();
        } catch (IOException e) {
            throw new SystemException(e);
        }


        var source = StringUtils.stripToEmpty(Optional.ofNullable(sharingRequest).map(SharingRequest::getSource).orElse("internet"));
        var urlPrefix = ConfigProvider.getConfig()
            .getOptionalValue("sharedlink." + source, String.class)
            .orElseThrow(() -> new BadRequestException("Invalid source: " + source));

        sharingRequest = Optional.ofNullable(sharingRequest).orElse(new SharingRequest());
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
