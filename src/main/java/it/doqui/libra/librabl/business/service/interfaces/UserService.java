package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.views.security.EditableUserDescriptor;
import it.doqui.libra.librabl.views.security.UserItem;
import it.doqui.libra.librabl.views.security.UserRequest;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public interface UserService {

    Paged<String> findGroups(String groupname, Pageable pageable);
    Paged<UserItem> findUsers(String username, boolean includeMetadata, boolean nameOnly, Pageable pageable);
    UserItem findUser(String username);
    Paged<UserItem> findGroupUsers(@NotNull String groupname, boolean includeMetadata, boolean nameOnly, Pageable pageable);
    String createGroup(@NotNull String groupname);
    UserItem createUser(@NotNull UserRequest u);
    Collection<UserItem> createUsers(Collection<UserRequest> users);
    void addUserToGroup(String username, String groupname);
    void removeUserFromGroup(String username, String groupname);
    void deleteGroup(String groupname);
    void deleteUser(String username);
    void updateUser(String username, EditableUserDescriptor item);
}
