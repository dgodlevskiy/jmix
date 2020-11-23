/*
 * Copyright 2020 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jmix.securitydata.user;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.security.GrantedAuthorityContainer;
import io.jmix.core.security.UserRepository;
import io.jmix.security.authentication.RoleGrantedAuthority;
import io.jmix.security.role.RoleRepository;
import io.jmix.security.role.assignment.RoleAssignment;
import io.jmix.security.role.assignment.RoleAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Abstract {@link UserRepository} that loads User entity from the database. A {@link UserRepository} generated in the
 * project may extend this class. It must override the {@link #getUserClass()} method.
 *
 * @param <T>
 */
public abstract class AbstractDatabaseUserRepository<T extends UserDetails> implements UserRepository {

    protected T systemUser;
    protected T anonymousUser;

    protected DataManager dataManager;
    protected Metadata metadata;
    protected RoleRepository roleRepository;
    protected RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    public void setDataManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Autowired
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @Autowired
    public void setRoleAssignmentRepository(RoleAssignmentRepository roleAssignmentRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Autowired
    public void setRoleRepository(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @PostConstruct
    private void init() {
        systemUser = createSystemUser();
        anonymousUser = createAnonymousUser();
    }

    /**
     * Method returns an actual class of the User entity used in the project
     */
    protected abstract Class<T> getUserClass();

    protected T createSystemUser() {
        T systemUser = metadata.create(getUserClass());
        EntityValues.setValue(systemUser, "username", "system");
        return systemUser;
    }

    protected T createAnonymousUser() {
        T anonymousUser = metadata.create(getUserClass());
        EntityValues.setValue(anonymousUser, "username", "anonymous");
        if (anonymousUser instanceof GrantedAuthorityContainer) {
            ((GrantedAuthorityContainer) anonymousUser).setAuthorities(createAuthorities("anonymous"));
        }
        return anonymousUser;
    }

    @Override
    public T getSystemUser() {
        return systemUser;
    }

    @Override
    public T getAnonymousUser() {
        return anonymousUser;
    }

    @Override
    public List<T> getByUsernameLike(String username) {
        //todo view
        return dataManager.load(getUserClass())
                .query("where e.username like :username")
                .parameter("username", "%" + username + "%")
                .list();
    }

    @Override
    public T loadUserByUsername(String username) throws UsernameNotFoundException {
        //todo view
        List<T> users = dataManager.load(getUserClass())
                .query("where e.username = :username")
                .parameter("username", username)
                .list();
        if (!users.isEmpty()) {
            T user = users.get(0);
            if (user instanceof GrantedAuthorityContainer) {
                ((GrantedAuthorityContainer) user).setAuthorities(createAuthorities(username));
            }
            return user;
        } else {
            throw new UsernameNotFoundException("User not found");
        }
    }

    protected Collection<? extends GrantedAuthority> createAuthorities(String username) {
        return roleAssignmentRepository.getAssignmentsByUsername(username).stream()
                .map(RoleAssignment::getRoleCode)
                .map(role -> roleRepository.getRoleByCode(role))
                .filter(Objects::nonNull)
                .map(RoleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
