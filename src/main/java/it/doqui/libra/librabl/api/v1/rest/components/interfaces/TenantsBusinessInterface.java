package it.doqui.libra.librabl.api.v1.rest.components.interfaces;

import it.doqui.libra.librabl.api.v1.rest.dto.*;

public interface TenantsBusinessInterface {

    // none
    boolean onlineReindex(ReindexParameters params) ;

    // none
    void createTenant(Tenant tenant);

    // done
    String createGroup(String groupName, String parentGroupName);

    // done
    void addUserToGroup(String userName, String groupName);

    // done
    void removeUserFromGroup(String userName, String groupName);

    // done
    String createUser(User user);

    // done
    void deleteGroup(String groupName);

    // done
    void deleteUser(String userName);

    // done
    void updateUser(User user, Boolean updatePassword);

    // done
    String[] listAllGroups(String filter);

    // done
    User[] listUsers(String groupName);

    // done
    String[] listAllUserNames(String filter);

    // none
    void deployCustomModel(CustomModel customModel, byte[] bytes);

    // none
    void undeployCustomModel(String modelName);

    // done
    CustomModel[] getAllCustomModels();

    // done
    ModelMetadata getModelDefinition(String prefixedName);

    // done
    void addPublicKey(String publicKey);

    // done
    String[] getPublicKeys();

    // done
    void removePublicKey(String publicKey);

    // done
    Tenant[] listAllTenantNames();    

    // done
    User[] listAllUsers(String filter);

}