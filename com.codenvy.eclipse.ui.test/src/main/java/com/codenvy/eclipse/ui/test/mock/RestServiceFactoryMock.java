/*
 * CODENVY CONFIDENTIAL
 * ________________
 * 
 * [2012] - [2014] Codenvy, S.A.
 * All Rights Reserved.
 * NOTICE: All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any. The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.eclipse.ui.test.mock;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import com.codenvy.eclipse.core.AuthenticationService;
import com.codenvy.eclipse.core.ProjectService;
import com.codenvy.eclipse.core.RestService;
import com.codenvy.eclipse.core.RestServiceFactory;
import com.codenvy.eclipse.core.RestServiceWithAuth;
import com.codenvy.eclipse.core.UserService;
import com.codenvy.eclipse.core.WorkspaceService;
import com.codenvy.eclipse.core.model.CodenvyToken;

/**
 * The {@linkplain RestServiceFactory} mock implementation.
 * 
 * @author Kevin Pollet
 */
public class RestServiceFactoryMock implements RestServiceFactory {
    private final Map<Class< ? extends RestService>, Class< ? >>         restServiceBindings;
    private final Map<Class< ? extends RestServiceWithAuth>, Class< ? >> restServiceWithAuthBindings;

    public RestServiceFactoryMock() {
        this.restServiceBindings = new HashMap<>();
        this.restServiceBindings.put(AuthenticationService.class, AuthenticationServiceMock.class);

        this.restServiceWithAuthBindings = new HashMap<>();
        this.restServiceWithAuthBindings.put(WorkspaceService.class, WorkspaceServiceMock.class);
        this.restServiceWithAuthBindings.put(UserService.class, UserServiceMock.class);
        this.restServiceWithAuthBindings.put(ProjectService.class, ProjectServiceMock.class);
    }

    @Override
    public <T extends RestService, S extends T> T newRestService(Class<T> clazz, String url) {
        checkNotNull(clazz);

        try {

            @SuppressWarnings("unchecked")
            final Class<S> impl = (Class<S>)restServiceBindings.get(clazz);
            final Constructor<S> constructor = impl.getConstructor(String.class);
            return constructor.newInstance(url);

        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
            | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends RestServiceWithAuth, S extends T> T newRestServiceWithAuth(Class<T> clazz, String url, CodenvyToken token) {
        checkNotNull(clazz);

        try {

            @SuppressWarnings("unchecked")
            final Class<S> impl = (Class<S>)restServiceWithAuthBindings.get(clazz);
            final Constructor<S> constructor = impl.getConstructor(String.class, CodenvyToken.class);
            return constructor.newInstance(url, token);

        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
            | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}