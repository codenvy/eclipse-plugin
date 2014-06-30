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
package com.codenvy.eclipse.ui.wizard.importer;

import static com.codenvy.eclipse.core.utils.EclipseProjectHelper.createIProjectFromZipStream;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;

import com.codenvy.eclipse.client.model.Project;
import com.codenvy.eclipse.core.CodenvyPlugin;
import com.codenvy.eclipse.core.team.CodenvyMetaProject;
import com.codenvy.eclipse.ui.wizard.importer.pages.AuthenticationWizardPage;
import com.codenvy.eclipse.ui.wizard.importer.pages.ProjectWizardPage;

/**
 * Wizard used to import Codenvy projects from the given Codenvy platform.
 * 
 * @author Kevin Pollet
 * @author Stéphane Daviet
 */
public class ImportProjectFromCodenvyWizard extends Wizard implements IImportWizard, INewWizard {
    private final AuthenticationWizardPage authenticationWizardPage;
    private final ProjectWizardPage        projectWizardPage;

    /**
     * Default constructor.
     */
    public ImportProjectFromCodenvyWizard() {
        this.authenticationWizardPage = new AuthenticationWizardPage();
        this.projectWizardPage = new ProjectWizardPage();

        setWindowTitle("Import Codenvy Projects");
        setNeedsProgressMonitor(true);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
    }

    @Override
    public void addPages() {
        addPage(authenticationWizardPage);
        addPage(projectWizardPage);
    }

    @Override
    public void createPageControls(Composite pageContainer) {
        authenticationWizardPage.createControl(pageContainer);
        // workspace and project pages are created lazily
    }

    @Override
    public void setContainer(IWizardContainer wizardContainer) {
        super.setContainer(wizardContainer);

        if (wizardContainer != null) {
            final WizardDialog wizardDialog = (WizardDialog)wizardContainer;
            wizardDialog.addPageChangingListener(authenticationWizardPage);
            wizardDialog.addPageChangedListener(projectWizardPage);
        }
    }

    @Override
    public boolean canFinish() {
        final IWizardPage currentWizardPage = getContainer().getCurrentPage();

        return currentWizardPage != null
               && currentWizardPage.getName().equals(projectWizardPage.getName())
               && currentWizardPage.isPageComplete();
    }

    @Override
    public boolean performFinish() {
        final String platformURL = authenticationWizardPage.getURL();
        final String username = authenticationWizardPage.getUsername();
        final List<IWorkingSet> workingSets = projectWizardPage.getWorkingSets();
        final List<Project> projects = projectWizardPage.getProjects();
        final IWorkbench workbench = PlatformUI.getWorkbench();

        try {

            workbench.getProgressService().run(true, false, new IRunnableWithProgress() {
                @Override
                public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    monitor.beginTask("Importing projects", projects.size());

                    final List<IProject> importedProjects = new ArrayList<>();
                    for (final Project oneProject : projects) {
                        final IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
                        final IPath newProjectLocation = workspaceLocation != null ? workspaceLocation.append(oneProject.name) : null;

                        if (newProjectLocation != null && newProjectLocation.toFile().exists()) {
                            Display.getDefault().syncExec(new Runnable() {
                                @Override
                                public void run() {
                                    final String message = "Project with name '" + oneProject.name + "' exists in workspace, override?";
                                    final boolean override = MessageDialog.openQuestion(getShell(), "Override existing project", message);
                                    if (override) {
                                        deleteDirectory(newProjectLocation.toFile());
                                    }
                                }
                            });
                        }

                        if (newProjectLocation == null || !newProjectLocation.toFile().exists()) {
                            final IProject importedProject = importProject(platformURL, username, oneProject, monitor);
                            importedProjects.add(importedProject);
                        }

                        monitor.worked(1);
                    }

                    final IWorkingSetManager workingSetManager = workbench.getWorkingSetManager();
                    for (IAdaptable importedProject : importedProjects) {
                        workingSetManager.addToWorkingSets(importedProject,
                                                           workingSets.toArray(new IWorkingSet[workingSets.size()]));
                    }
                }
            });

        } catch (InvocationTargetException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    public AuthenticationWizardPage getAuthenticationWizardPage() {
        return authenticationWizardPage;
    }

    public ProjectWizardPage getProjectWizardPage() {
        return projectWizardPage;
    }

    /**
     * Imports the given Codenvy project into Eclipse.
     * 
     * @param platformURL the Codenvy platform URL.
     * @param username the user name.
     * @param project the Codenvy {@link Project} to import.
     * @param monitor the {@link IProgressMonitor}.
     * @return the imported {@link IProject} reference.
     */
    private IProject importProject(String platformURL, String username, Project project, IProgressMonitor monitor) {
        final ZipInputStream zipInputStream = CodenvyPlugin.getDefault()
                                                           .getCodenvyBuilder(platformURL, username)
                                                           .build()
                                                           .project()
                                                           .exportResources(project, null)
                                                           .execute();

        return createIProjectFromZipStream(zipInputStream,
                                           new CodenvyMetaProject(platformURL, username, project.name, project.workspaceId), monitor);
    }

    /**
     * Deletes the given directory and it's sub folders and files.
     * 
     * @param directory the directory to delete.
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            for (File oneFile : directory.listFiles()) {
                if (oneFile.isDirectory()) {
                    deleteDirectory(oneFile);
                }
                else {
                    oneFile.delete();
                }
            }
            directory.delete();
        }
    }
}
