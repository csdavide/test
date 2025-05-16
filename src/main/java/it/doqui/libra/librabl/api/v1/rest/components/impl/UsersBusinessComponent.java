package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.libra.librabl.api.v1.cxf.impl.UserServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.TenantsBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.User;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;

import java.util.Arrays;

@ApplicationScoped
@Slf4j
public class UsersBusinessComponent implements BusinessComponent {

    @Inject
    UserServiceBridge userService;

    @Inject
    DtoMapper dtoMapper;

    @Inject
    ModelMapper modelMapper;

    @Override
    public Class<?> getComponentInterface() {
        return TenantsBusinessInterface.class;
    }

    public String[] listAllGroups(String filter) {
        var filterGroup = new it.doqui.index.ecmengine.mtom.dto.Group();
        filterGroup.setName(filter);
        return Arrays.stream(userService.listAllGroups(filterGroup, null))
            .map(it.doqui.index.ecmengine.mtom.dto.Group::getName)
            .toList()
            .toArray(new String[0]);
    }

    public String[] listAllUserNames(String filter) {
        var filterUser = new it.doqui.index.ecmengine.mtom.dto.User();
        filterUser.setUsername(filter);
        return Arrays.stream(userService.listAllUserNames(filterUser, null))
            .map(it.doqui.index.ecmengine.mtom.dto.User::getUsername)
            .toList()
            .toArray(new String[0]);
    }

    public User[] listAllUsers(String filter) {
        var filterUser = new it.doqui.index.ecmengine.mtom.dto.User();
        filterUser.setUsername(filter);
        return Arrays.stream(userService.listAllUserNames(filterUser, null))
            .map(u -> dtoMapper.convert(u, User.class))
            .toList()
            .toArray(new User[0]);
    }

    public User[] listUsers(String groupName) {
        var group = new it.doqui.index.ecmengine.mtom.dto.Group();
        group.setName(groupName);
        return Arrays.stream(userService.listUsers(group, null))
            .map(u -> dtoMapper.convert(u, User.class))
            .toList()
            .toArray(new User[0]);
    }

    public String createGroup(String groupName, String parentGroupName) {
        var group = new it.doqui.index.ecmengine.mtom.dto.Group();
        group.setName(groupName);
        return userService.createGroup(group, null, null);
    }

    public void deleteGroup(String groupName) {
        var group = new it.doqui.index.ecmengine.mtom.dto.Group();
        group.setName(groupName);
        userService.deleteGroup(group, null);
    }

    public String createUser(User user) {
        var u = modelMapper.map(user, it.doqui.index.ecmengine.mtom.dto.User.class);
        return userService.createUser(u, null);
    }

    public void updateUser(User user, Boolean updatePassword) {
        var u = modelMapper.map(user, it.doqui.index.ecmengine.mtom.dto.User.class);
        if (updatePassword) {
            userService.updateUserPassword(u, null);
        } else {
            userService.updateUserMetadata(u, null);
        }
    }

    public void deleteUser(String userName) {
        var user = new it.doqui.index.ecmengine.mtom.dto.User();
        user.setUsername(userName);
        userService.deleteUser(user, null);
    }

    public void addUserToGroup(String userName, String groupName) {
        var user = new it.doqui.index.ecmengine.mtom.dto.User();
        user.setUsername(userName);
        var group = new it.doqui.index.ecmengine.mtom.dto.Group();
        group.setName(groupName);
        userService.addUserToGroup(user, group, null);
    }

    public void removeUserFromGroup(String userName, String groupName) {
        var user = new it.doqui.index.ecmengine.mtom.dto.User();
        user.setUsername(userName);
        var group = new it.doqui.index.ecmengine.mtom.dto.Group();
        group.setName(groupName);
        userService.removeUserFromGroup(user, group, null);
    }
}
