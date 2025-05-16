package it.doqui.libra.librabl.business.service.interfaces;

public interface Constants {

    String WORKSPACE = "workspace";
    String CONTENT_ATTR_URL = "contentUrl";

    String ASPECT_SYS_REFERENCEABLE = "sys:referenceable";
    String ASPECT_SYS_ARCHIVED = "sys:archived";
    String ASPECT_CM_AUDITABLE = "cm:auditable";
    String ASPECT_CM_OWNABLE = "cm:ownable";
    String ASPECT_CM_VERSIONABLE = "cm:versionable";
    String ASPECT_CM_WORKINGCOPY = "cm:workingcopy";
    String ASPECT_ECMSYS_DISABLED_FULLTEXT = "ecm-sys:disabledFulltext";
    String ASPECT_ECMSYS_INDEXING_REQUIRED = "ecm-sys:indexingRequired";
    String ASPECT_ECMSYS_EPHEMERAL = "ecm-sys:ephemeral";
    String ASPECT_ECMSYS_DELETED = "ecm-sys:deleted";
    String ASPECT_ECMSYS_EXPIRABLE = "ecm-sys:expirable";
    String ASPECT_ECMSYS_ENCRYPTED = "ecm-sys:encrypted";
    String ASPECT_ECMSYS_ASYNCREQUIRED = "ecm-sys:asynchRequired";
    String ASPECT_ECMSYS_LOCALIZABLE = "ecm-sys:localizable";
    String ASPECT_ECMSYS_STREAMEDCONTENT = "ecm-sys:streamedContent";
    String ASPECT_ECMSYS_TRANSFORMER = "ecm-sys:renditionTransformer";
    String ASPECT_ECMSYS_RENDITIONABLE = "ecm-sys:renditionable";
    String ASPECT_ECMSYS_RENDITION = "ecm-sys:rendition";
    String ASPECT_ECMSYS_HASHABLE = "ecm-sys:hashable";
    String ASPECT_ECMSYS_SHARED = "ecm-sys:shared";
    String ASPECT_COPIED_NODE = "cm:copiedfrom";

    String CM_NAME = "cm:name";
    String CM_FOLDER = "cm:folder";
    String CM_CREATOR = "cm:creator";
    String CM_CREATED = "cm:created";
    String CM_MODIFIER = "cm:modifier";
    String CM_MODIFIED = "cm:modified";
    String CM_OWNER = "cm:owner";
    String CM_CONTAINS = "cm:contains";
    String CM_CONTENT = "cm:content";
    String CM_TITLE = "cm:title";
    String CM_TITLED = "cm:titled";
    String CM_DESCRIPTION = "cm:description";
    String CM_RENDITIONS = "cm:renditions";
    String CM_VERSION_LABEL = "cm:versionLabel";
    String CM_INITIAL_VERSION = "cm:initialVersion";
    String CM_AUTO_VERSION = "cm:autoVersion";
    String CM_WORKINGCOPY_OWNER = "cm:workingCopyOwner";
    String PROP_SYS_ARCHIVEDBY = "sys:archivedBy";
    String PROP_SYS_ARCHIVEDDATE = "sys:archivedDate";
    String PROP_CM_COPIED_NODE = "cm:source";

    String PROP_ECMSYS_LOCALE = "ecm-sys:locale";
    String PROP_ECMSYS_XSLID = "ecm-sys:xslId";
    String PROP_ECMSYS_TRANSFORMER_DESCRIPTION = "ecm-sys:transformerDescription";
    String PROP_ECMSYS_RENDITION_DESCRIPTION = "ecm-sys:renditionDescription";
    String PROP_ECMSYS_GENMIMETYPE = "ecm-sys:genMimeType";
    String PROP_ECMSYS_RENDITIONID = "ecm-sys:renditionId";
    String PROP_ECMSYS_EXPIRES_AT = "ecm-sys:expiresAt";
    String PROP_ECMSYS_SHARED_LINKS = "ecm-sys:sharedLinks";
    String PROP_ECMSYS_HASH = "ecm-sys:hash";
    String PROP_ECMSYS_RENDITIONMAP = "ecm-sys:renditionMap";
    String PROP_ECMSYS_GENERATED = "ecm-sys:generated";

    String MIMETYPE_TEXT_PLAIN = "text/plain";
    String MIMETYPE_TEXT_XML = "text/xml";
    String MIMETYPE_APPLICATION_XML = "application/xml";
    String MIMETYPE_XLSX_PROTECTED = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    String MIMETYPE_P7M = "application/pkcs7-mime";
    String MIMETYPE_P7S = "application/pkcs7-signature";

    long MASSIVE_MAX_RETRIEVE_SIZE = 9437184; // 9 MB, in Byte.
    String RENDITION_PATH = "/app:company_home/cm:renditions/";
    String TRANSFORMER_PATH = "/app:company_home/cm:renditions/cm:transformers/";
}
