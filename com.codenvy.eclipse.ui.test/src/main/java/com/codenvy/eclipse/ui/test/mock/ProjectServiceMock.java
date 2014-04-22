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

import java.util.ArrayList;
import java.util.List;

import com.codenvy.eclipse.core.ProjectService;
import com.codenvy.eclipse.core.model.CodenvyToken;
import com.codenvy.eclipse.core.model.Project;

/**
 * The Codenvy project client service mock.
 * 
 * @author Kevin Pollet
 */
public class ProjectServiceMock implements ProjectService {
    public static final String  MOCK_PROJECT_NAME        = "prj1";
    public static final String  MOCK_PROJECT_TYPE_NAME   = "spring";
    public static final String  MOCK_PROJECT_DESCRIPTION = "prj1-description";

    private final List<Project> projects;

    public ProjectServiceMock(String url, CodenvyToken codenvyToken) {
        this.projects = new ArrayList<>();
        this.projects.add(new Project(null, null, null, null, MOCK_PROJECT_TYPE_NAME, MOCK_PROJECT_NAME, MOCK_PROJECT_DESCRIPTION, null, null, null, null));
        this.projects.add(new Project(null, null, null, null, "java", "prj2", "prj2-description", null, null, null, null));
        this.projects.add(new Project(null, null, null, null, "angular", "prj3", "prj3-description", null, null, null, null));
        this.projects.add(new Project(null, null, null, null, "codenvy", "prj4", "prj4-description", null, null, null, null));
    }

    @Override
    public List<Project> getWorkspaceProjects(String workspaceId) {
        if (WorkspaceServiceMock.MOCK_WORKSPACE_ID.equals(workspaceId)) {
            return projects;
        }
        return new ArrayList<>();
    }

    @Override
    public Project newProject(Project project, String workspaceId) {
        throw new UnsupportedOperationException();
    }
}