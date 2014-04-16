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
package com.codenvy.eclipse.core.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import com.codenvy.eclipse.core.service.api.RestServiceFactory;
import com.codenvy.eclipse.core.service.api.WorkspaceService;
import com.codenvy.eclipse.core.service.api.model.CodenvyToken;
import com.codenvy.eclipse.core.service.api.model.Workspace;
import com.codenvy.eclipse.core.service.api.model.Workspace.WorkspaceRef;

/**
 * Test the workspace service.
 * 
 * @author Kevin Pollet
 */
public class WorkspaceServiceTest extends RestApiBaseTest {
    private WorkspaceService workspaceService;

    @Before
    public void initialize() {
        final BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        final ServiceReference<RestServiceFactory> restServiceFactoryRef = context.getServiceReference(RestServiceFactory.class);
        Assert.assertNotNull(restServiceFactoryRef);

        final RestServiceFactory restServiceFactory = context.getService(restServiceFactoryRef);
        Assert.assertNotNull(restServiceFactory);

        workspaceService = restServiceFactory.newRestServiceWithAuth(WorkspaceService.class, REST_API_URL, new CodenvyToken("dummy"));
    }

    @Test
    public void testGetAllWorkspaces() {
        final List<Workspace> workspaces = workspaceService.getAllWorkspaces();

        Assert.assertNotNull(workspaces);
        Assert.assertTrue(workspaces.size() > 0);
        Assert.assertNotNull(workspaces.get(0).workspaceRef);
        Assert.assertNull(workspaces.get(0).workspaceRef.id);
        Assert.assertNotNull(workspaces.get(0).workspaceRef.name);
    }

    @Test(expected = NullPointerException.class)
    public void testGetWorkspaceByNameWithNull() {
        workspaceService.getWorkspaceByName(null);
    }

    @Test
    public void testGetWorkspaceByName() {
        final WorkspaceRef workspaceRef = workspaceService.getWorkspaceByName("default");

        Assert.assertNotNull(workspaceRef);
        Assert.assertNotNull(workspaceRef.id);
        Assert.assertNotNull(workspaceRef.name);
    }
}
