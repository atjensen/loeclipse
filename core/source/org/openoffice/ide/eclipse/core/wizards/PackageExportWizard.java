/*************************************************************************
 *
 * $RCSfile: PackageExportWizard.java,v $
 *
 * $Revision: 1.11 $
 *
 * last change: $Author: cedricbosdo $ $Date: 2008/12/13 13:42:49 $
 *
 * The Contents of this file are made available subject to the terms of
 * the GNU Lesser General Public License Version 2.1
 *
 * Sun Microsystems Inc., October, 2000
 *
 *
 * GNU Lesser General Public License Version 2.1
 * =============================================
 * Copyright 2000 by Sun Microsystems, Inc.
 * 901 San Antonio Road, Palo Alto, CA 94303, USA
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1, as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 * 
 * The Initial Developer of the Original Code is: Sun Microsystems, Inc..
 *
 * Copyright: 2002 by Sun Microsystems, Inc.
 *
 * All Rights Reserved.
 *
 * Contributor(s): Cedric Bosdonnat
 *
 *
 ************************************************************************/
package org.openoffice.ide.eclipse.core.wizards;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.openoffice.ide.eclipse.core.OOEclipsePlugin;
import org.openoffice.ide.eclipse.core.internal.helpers.UnoidlProjectHelper;
import org.openoffice.ide.eclipse.core.model.IUnoidlProject;
import org.openoffice.ide.eclipse.core.model.ProjectsManager;
import org.openoffice.ide.eclipse.core.model.config.IOOo;
import org.openoffice.ide.eclipse.core.model.language.ILanguageBuilder;
import org.openoffice.ide.eclipse.core.model.pack.PackagePropertiesModel;
import org.openoffice.ide.eclipse.core.model.pack.UnoPackage;
import org.openoffice.ide.eclipse.core.utils.FileHelper;
import org.openoffice.ide.eclipse.core.wizards.pages.PackageExportWizardPage;

/**
 * A wizard to export the project as a UNO package.
 * 
 * @author cedricbosdo
 *
 */
public class PackageExportWizard extends Wizard implements IExportWizard {

    private IStructuredSelection mSelection;
    private PackageExportWizardPage mPage;
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performFinish() {
        
        IUnoidlProject prj = mPage.getProject();
        String extension = mPage.getPackageExtension();
        File outputDir = mPage.getOutputPath();
    
        new PackageExportJob(prj, extension, outputDir).schedule();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void init(IWorkbench pWorkbench, IStructuredSelection pSelection) {
        mSelection = pSelection;
        
        boolean canExport = false;
        
        Iterator<?> iter = mSelection.iterator();
        while (iter.hasNext()) {
            Object o = iter.next();
            if (o instanceof IAdaptable) {
                IAdaptable adaptable = (IAdaptable)o;
                IResource res = (IResource)adaptable.getAdapter(IResource.class);
                if (res != null && ProjectsManager.getProject(res.getProject().getName()) != null) {
                    canExport = true;
                }
            }
        }
        
        if (canExport) {
            mPage = new PackageExportWizardPage("main", mSelection); //$NON-NLS-1$
            addPage(mPage);
        }
    }

    /**
     * The class performing the package export task.
     * 
     * <p>This job doesn't perform the deployment into OpenOffice.org.</p>
     * 
     * @author cedricbosdo
     */
    private class PackageExportJob extends Job {

        private IUnoidlProject mPrj;
        private String mExtension;
        private File mOutputDir;
        
        /**
         * Constructor.
         * 
         * @param pPrj the project for which to create the package
         * @param pVersion the package version
         * @param pOutput the directory where to create the package.
         */
        public PackageExportJob(IUnoidlProject pPrj, String pVersion, File pOutput) {
            super(Messages.getString("PackageExportWizard.JobTitle")); //$NON-NLS-1$
            setPriority(Job.INTERACTIVE);
            
            mPrj = pPrj;
            mExtension = pVersion;
            mOutputDir = pOutput;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected IStatus run(IProgressMonitor pMonitor) {
            
            IStatus status = new Status(IStatus.ERROR, 
                    OOEclipsePlugin.OOECLIPSE_PLUGIN_ID, 
                    IStatus.ERROR, 
                    Messages.getString("PackageExportWizard.EXPORT_ERROR_MSG"), //$NON-NLS-1$
                    null);
            
            try {
                // Export the library
                IPath libraryPath = null;
                ILanguageBuilder langBuilder = mPrj.getLanguage().getLanguageBuidler();
                libraryPath = langBuilder.createLibrary(mPrj);
                
                // Create the package
                IPath prjPath = mPrj.getProjectPath();
                File dir = new File(prjPath.toOSString());
                File dest = new File(mOutputDir, 
                        mPrj.getName().replace(" ", "") //$NON-NLS-1$ //$NON-NLS-2$ 
                            + "." + mExtension); //$NON-NLS-1$
                UnoPackage unoPackage = UnoidlProjectHelper.createMinimalUnoPackage(mPrj, dest);      

                /*
                 *  Read the package.properties files to add user selected files.
                 *  Recognize the following types from their extensions:
                 *      - .xcu, xcs
                 *      - .rdb
                 */
                IFile pkgProperties = mPrj.getFile("package.properties"); //$NON-NLS-1$
                if (pkgProperties.exists()) {
                    PackagePropertiesModel pkgModel = new PackagePropertiesModel(pkgProperties);

                    List<IFolder> basicLibs = pkgModel.getBasicLibraries();
                    for (IFolder lib : basicLibs) {
                        unoPackage.addBasicLibraryFile(lib);
                    }

                    List<IFolder> dialogLibs = pkgModel.getDialogLibraries();
                    for (IFolder lib : dialogLibs) {
                        unoPackage.addDialogLibraryFile(lib);
                    }

                    List<IResource> contents = pkgModel.getContents();
                    for (IResource res : contents) {
                        unoPackage.addContent(res);
                    }

                    Map<Locale, IFile> descriptions = pkgModel.getDescriptionFiles();
                    Iterator<Entry<Locale, IFile>> iter = descriptions.entrySet().iterator();
                    while (iter.hasNext()) {
                        Entry<Locale, IFile> entry = iter.next();
                        unoPackage.addPackageDescription(entry.getValue(), 
                                entry.getKey());
                    }
                }

                // Close and write the package
                dest = unoPackage.close();

                // Clean up the library file and META-INF directory
                FileHelper.remove(new File(dir, "META-INF")); //$NON-NLS-1$
                File libFile = new File(libraryPath.toOSString());
                FileHelper.remove(libFile);

                // Refresh the project and return the status
                UnoidlProjectHelper.refreshProject(mPrj, pMonitor);

                // Propose to update the package in OpenOffice.org instance
                Display.getDefault().asyncExec(new DeployerJob(mPrj.getOOo(), dest));

                status = new Status(IStatus.OK, 
                        OOEclipsePlugin.OOECLIPSE_PLUGIN_ID, 
                        IStatus.OK, 
                        Messages.getString("PackageExportWizard.ExportedMessage"), //$NON-NLS-1$
                        null);
                
            } catch (Exception e) {
                // the error status is already defined at the beginning of the method  
                status = new Status(IStatus.ERROR, 
                        OOEclipsePlugin.OOECLIPSE_PLUGIN_ID, 
                        IStatus.ERROR, 
                        Messages.getString("PackageExportWizard.EXPORT_ERROR_MSG"), //$NON-NLS-1$
                        e );
            }
                
            return status;
        }
    }
    
    /**
     * Thread performing the package deployment into OpenOffice.org.
     * 
     * @author cedricbosdo
     *
     */
    class DeployerJob implements Runnable {
        
        private IOOo mOOo;
        private File mDest;
        
        /**
         * Constructor.
         * 
         * @param pOoo the OpenOffice.org where to deploy
         * @param pDest the package to deploy
         */
        DeployerJob(IOOo pOoo, File pDest) {
            mOOo = pOoo;
            mDest = pDest;
        }
        
        /**
         * {@inheritDoc}
         */
        public void run() {
            if (mOOo.canManagePackages()) {
                // Ask to update the package
                if (MessageDialog.openQuestion(Display.getDefault().getActiveShell(), 
                        Messages.getString("PackageExportWizard.DeployPackageTitle"),  //$NON-NLS-1$
                        Messages.getString("PackageExportWizard.DeployPackageMessage"))) { //$NON-NLS-1$
                    mOOo.updatePackage(mDest);
                }
            }
        }
    }
}