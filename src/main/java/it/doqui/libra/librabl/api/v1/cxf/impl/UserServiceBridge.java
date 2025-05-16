package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.Group;
import it.doqui.index.ecmengine.mtom.dto.MtomOperationContext;
import it.doqui.index.ecmengine.mtom.dto.User;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.business.service.interfaces.UserService;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.security.EditableUserDescriptor;
import it.doqui.libra.librabl.views.security.UserItem;
import it.doqui.libra.librabl.views.security.UserRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
@SuppressWarnings("unused")
public class UserServiceBridge extends AbstractServiceBridge {

    @Inject
    UserService userService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public User[] listAllUsers(User filter, MtomOperationContext context)
        throws InvalidCredentialsException, NoDataExtractedException, InvalidParameterException,
        EcmEngineTransactionException, EcmEngineException {
        return listAllUsers(filter, true, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public User[] listAllUserNames(User filter, MtomOperationContext context)
        throws InvalidCredentialsException, NoDataExtractedException, InvalidParameterException,
        EcmEngineTransactionException, EcmEngineException {
        return listAllUsers(filter, false, context);
    }

    private User[] listAllUsers(User filter, boolean includeMetadata, MtomOperationContext context)
        throws InvalidCredentialsException, NoDataExtractedException, InvalidParameterException,
        EcmEngineTransactionException, EcmEngineException {
        validate(() -> {
            Objects.requireNonNull(filter, "Filter cannot be null");
            Objects.requireNonNull(filter.getUsername(), "Filter username cannot be blank. Insert '*' for listing all users.");
            if (StringUtils.isBlank(filter.getUsername())) {
                throw new NoDataExtractedException(filter.getUsername());
            }
        });

        var result = call(context, () -> userService
            .findUsers(filter.getUsername(), includeMetadata, false, null)
            .map(this::map)
            .getItems()
            .toArray(new User[0])
        );

        if (result.length == 0) {
            throw new NoDataExtractedException(filter.getUsername());
        }

        return result;
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Group[] listAllGroups(Group filter, MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        EcmEngineException, NoDataExtractedException {
        validate(() -> {
            Objects.requireNonNull(filter, "Filter cannot be null");
            if (StringUtils.isBlank(filter.getName())) {
                throw new NoDataExtractedException(filter.getName());
            }
        });

        var result = call(context, () -> userService
            .findGroups(filter.getName(), null)
            .map(g -> {
                Group r = new Group();
                r.setName(g);
                return r;
            })
            .getItems()
            .toArray(new Group[0])
        );

        if (result.length == 0) {
            throw new NoDataExtractedException(filter.getName());
        }

        return result;
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public User[] listUsers(Group gruppo, MtomOperationContext context)
        throws EcmEngineTransactionException, EcmEngineException, InvalidParameterException,
        InvalidCredentialsException, NoDataExtractedException {
        validate(() -> {
            Objects.requireNonNull(gruppo, "Group cannot be null");
            Objects.requireNonNull(gruppo.getName(), "Group name cannot be null");
            if (StringUtils.isBlank(gruppo.getName())) {
                throw new NoDataExtractedException(gruppo.getName());
            }
        });

        return call(context, () -> {
            try {
                var result = userService
                    .findGroupUsers(gruppo.getName(), true, false, null)
                    .map(this::map)
                    .getItems()
                    .toArray(new User[0]);

                if (result.length == 0) {
                    throw new NoDataExtractedException(gruppo.getName());
                }

                return result;
            } catch (NotFoundException e) {
                throw new EcmEngineException(e.getMessage());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public String createGroup(Group nuovoGruppo, Group gruppoPadre, MtomOperationContext context)
        throws GroupCreateException, GroupAlreadyExistsException, InvalidParameterException,
        NoSuchGroupException, EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(() -> {
            Objects.requireNonNull(nuovoGruppo, "The new group cannot be null");
            ObjectUtils.requireNotBlank(nuovoGruppo.getName(), "The new group name cannot be blank");
            if (gruppoPadre != null) {
                throw new InvalidParameterException("Parent group must be null: subgroup not supported");
            }
        });

        return call(context, () -> {
            try {
                return userService.createGroup(nuovoGruppo.getName());
            } catch (ConflictException e) {
                throw new GroupAlreadyExistsException(nuovoGruppo.getName());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public String createUser(User nuovoUtente, MtomOperationContext context)
        throws UserCreateException, UserAlreadyExistsException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(() -> {
            Objects.requireNonNull(nuovoUtente, "The user cannot be null");
            ObjectUtils.requireNotBlank(nuovoUtente.getUsername(), "The username cannot be blank");
            ObjectUtils.requireNull(StringUtils.stripToNull(nuovoUtente.getHomeFolderPath()), "Home folder cannot be specified");
            ObjectUtils.requireNull(nuovoUtente.getOrganizationId(), "Organization id is no more supported");
        });

        return call(context, () -> {
            try {
                return userService.createUser(map(nuovoUtente)).getUsername();
            } catch (ConflictException e) {
                throw new UserAlreadyExistsException(nuovoUtente.getName());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void updateUserMetadata(User utente, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchUserException, UserUpdateException, EcmEngineTransactionException {
        validate(() -> {
            Objects.requireNonNull(utente, "The user cannot be null");
            ObjectUtils.requireNotBlank(utente.getUsername(), "The username cannot be blank");
            ObjectUtils.requireNotBlank(utente.getSurname(), "The surname cannot be blank");
        });

        call(context, () -> {
            userService.updateUser(utente.getUsername(), asEditable(utente));
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void updateUserPassword(User utente, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchUserException, UserUpdateException, EcmEngineTransactionException {
        validate(() -> {
            Objects.requireNonNull(utente, "The user cannot be null");
            ObjectUtils.requireNotBlank(utente.getUsername(), "The username cannot be blank");
        });

        call(context, () -> {
            var u = new EditableUserDescriptor();
            u.setPassword(StringUtils.isBlank(utente.getPassword()) ? Optional.empty() : Optional.of(utente.getPassword().toCharArray()));
            userService.updateUser(utente.getUsername(), u);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void addUserToGroup(User utente, Group gruppo, MtomOperationContext context) throws GroupEditException,
        NoSuchUserException, NoSuchGroupException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(() -> {
            Objects.requireNonNull(utente, "The user cannot be null");
            ObjectUtils.requireNotBlank(utente.getUsername(), "The username cannot be blank");
            Objects.requireNonNull(gruppo, "The group cannot be null");
            ObjectUtils.requireNotBlank(gruppo.getName(), "The group name cannot be blank");
        });

        call(context, () -> {
            try {
                userService.addUserToGroup(utente.getUsername(), gruppo.getName());
            } catch (NotFoundException e) {
                if (StringUtils.startsWith(e.getMessage(), "Group")) {
                    throw new NoSuchGroupException(gruppo.getName());
                } else if (StringUtils.startsWith(e.getMessage(), "User")) {
                    throw new NoSuchUserException(utente.getName());
                }
                throw e;
            } catch (ConflictException e) {
                return null;
            }

            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void removeUserFromGroup(User utente, Group gruppo, MtomOperationContext context) throws GroupEditException,
        NoSuchUserException, NoSuchGroupException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(() -> {
            Objects.requireNonNull(utente, "The user cannot be null");
            ObjectUtils.requireNotBlank(utente.getUsername(), "The username cannot be blank");
            Objects.requireNonNull(gruppo, "The group cannot be null");
            ObjectUtils.requireNotBlank(gruppo.getName(), "The group name cannot be blank");
        });

        call(context, () -> {
            try {
                userService.removeUserFromGroup(utente.getUsername(), gruppo.getName());
            } catch (NotFoundException e) {
                if (StringUtils.startsWith(e.getMessage(), "Group")) {
                    throw new NoSuchGroupException(gruppo.getName());
                } else if (StringUtils.startsWith(e.getMessage(), "User")) {
                    throw new NoSuchUserException(utente.getName());
                }

                throw e;
            } catch (PreconditionFailedException e) {
                return null;
            }

            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void deleteGroup(Group gruppo, MtomOperationContext context)
        throws InvalidParameterException, GroupDeleteException, NoSuchGroupException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(() -> {
            Objects.requireNonNull(gruppo, "The group cannot be null");
            ObjectUtils.requireNotBlank(gruppo.getName(), "The group name cannot be blank");
        });

        call(context, () -> {
            try {
                userService.deleteGroup(gruppo.getName());
            } catch (NotFoundException e) {
                throw new NoSuchGroupException(gruppo.getName());
            }
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void deleteUser(User utente, MtomOperationContext context)
        throws InvalidParameterException, GroupDeleteException, NoSuchGroupException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(() -> {
            Objects.requireNonNull(utente, "The user cannot be null");
            ObjectUtils.requireNotBlank(utente.getUsername(), "The username cannot be blank");
        });

        call(context, () -> {
            try {
                userService.deleteUser(utente.getUsername());
            } catch (NotFoundException e) {
                throw new NoSuchUserException(utente.getUsername());
            }
            return null;
        });
    }

    private User map(UserItem u) {
        User r = new User();
        r.setUsername(u.getUsername());
        r.setName(u.getFirstName());
        r.setSurname(u.getLastName());
        r.setHomeFolderPath(u.getHomePath());
        return r;
    }

    private UserRequest map(User u) {
        UserRequest r = new UserRequest();
        r.setUsername(u.getUsername());
        r.setFirstName(u.getName());
        r.setLastName(u.getSurname());
        r.setPassword(u.getPassword().toCharArray());
        r.setHomeRequired(true);
        return r;
    }

    private EditableUserDescriptor asEditable(User u) {
        var r = new EditableUserDescriptor();
        if (u.getName() != null) {
            r.setFirstName(StringUtils.isBlank(u.getName()) ? Optional.empty() : Optional.of(u.getName()));
        }

        if (u.getSurname() != null) {
            r.setLastName(StringUtils.isBlank(u.getSurname()) ? Optional.empty() : Optional.of(u.getSurname()));
        }

        return r;
    }
}
