/*******************************************************************************
 * Copyright (c) 2014 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package com.codenvy.eclipse.core.team;

import static org.eclipse.core.resources.IResource.ROOT;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.RepositoryProvider;

import com.codenvy.eclipse.client.Codenvy;
import com.codenvy.eclipse.client.auth.AuthenticationException;
import com.codenvy.eclipse.client.model.Project;
import com.codenvy.eclipse.core.CodenvyPlugin;

/**
 * The Codenvy resource mapping class.
 * 
 * @author Kevin Pollet
 */
public class CodenvyMetaResource {
    private final IResource resource;
    private boolean         tracked;

    public CodenvyMetaResource(IResource resource) {
        this.resource = resource;
        this.tracked = false;

        if (resource.getType() != ROOT) {
            final CodenvyProvider codenvyProvider =
                                                    (CodenvyProvider)RepositoryProvider.getProvider(resource.getProject(),
                                                                                                    CodenvyProvider.PROVIDER_ID);

            if (codenvyProvider != null) {
                final CodenvyMetaProject metaProject = codenvyProvider.getMetaProject();

                if (metaProject != null) {
                    final Project codenvyProject = new Project.Builder().withName(metaProject.projectName)
                                                                        .withWorkspaceId(metaProject.workspaceId)
                                                                        .build();

                    final Codenvy codenvy = CodenvyPlugin.getDefault()
                                                         .getCodenvyBuilder(metaProject.url, metaProject.username)
                                                         .build();

                    try {

                        final String resourcePath = resource.getProjectRelativePath().toString();
                        this.tracked = codenvy.project()
                                              .isResource(codenvyProject, resourcePath)
                                              .execute();

                    } catch (AuthenticationException e) {
                        this.tracked = false;
                    }
                }
            }
        }
    }

    /**
     * Returns the underlying {@link IResource}.
     * 
     * @return the underlying {@link IResource}.
     */
    public IResource getResource() {
        return resource;
    }

    /**
     * Returns if this resource is tracked.
     * 
     * @return {@code true} if the resource is tracked, {@code false} otherwise.
     */
    public boolean isTracked() {
        return tracked;
    }
}
