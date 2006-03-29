package org.python.pydev.plugin;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry;
import org.eclipse.ui.editors.text.templates.ContributionTemplateStore;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.python.copiedfromeclipsesrc.PydevFileEditorInput;
import org.python.pydev.core.ICallback;
import org.python.pydev.core.IInterpreterManager;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.REF;
import org.python.pydev.core.Tuple;
import org.python.pydev.core.bundle.BundleInfo;
import org.python.pydev.core.bundle.IBundleInfo;
import org.python.pydev.core.bundle.ImageCache;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.editor.codecompletion.shell.AbstractShell;
import org.python.pydev.editor.templates.PyContextType;
import org.python.pydev.plugin.nature.PythonNature;
import org.python.pydev.plugin.nature.SystemPythonNature;
import org.python.pydev.pyunit.ITestRunListener;
import org.python.pydev.pyunit.PyUnitTestRunner;
import org.python.pydev.ui.interpreters.JythonInterpreterManager;
import org.python.pydev.ui.interpreters.PythonInterpreterManager;


/**
 * The main plugin class - initialized on startup - has resource bundle for internationalization - has preferences
 */
public class PydevPlugin extends AbstractUIPlugin implements Preferences.IPropertyChangeListener {
    // ----------------- SINGLETON THINGS -----------------------------
    public static IBundleInfo info;
    public static IBundleInfo getBundleInfo(){
        if(PydevPlugin.info == null){
            PydevPlugin.info = new BundleInfo(PydevPlugin.getDefault().getBundle());
        }
        return PydevPlugin.info;
    }
    public static void setBundleInfo(IBundleInfo b){
        PydevPlugin.info = b;
    }
    // ----------------- END BUNDLE INFO THINGS --------------------------
	
    private static IInterpreterManager pythonInterpreterManager;
    private static IInterpreterManager jythonInterpreterManager;
    public static void setPythonInterpreterManager(IInterpreterManager interpreterManager) {
        PydevPlugin.pythonInterpreterManager = interpreterManager;
    }
    public static IInterpreterManager getPythonInterpreterManager() {
        return pythonInterpreterManager;
    }

    public static void setJythonInterpreterManager(IInterpreterManager interpreterManager) {
        PydevPlugin.jythonInterpreterManager = interpreterManager;
    }
    public static IInterpreterManager getJythonInterpreterManager() {
        return jythonInterpreterManager;
    }
    // ----------------- END SINGLETON THINGS --------------------------

    /**
     * returns the interpreter manager for a given nature
     * @param nature the nature from where we want to get the associated interpreter manager
     * 
     * @return the interpreter manager
     */
    public static IInterpreterManager getInterpreterManager(IPythonNature nature) {
        try {
            if (nature.isJython()) {
                return jythonInterpreterManager;
            } else if (nature.isPython()) {
                return pythonInterpreterManager;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Unable to get the interpreter manager for the nature passed.");
    }
    
    
    private static PydevPlugin plugin; //The shared instance.
    
    private ResourceBundle resourceBundle; //Resource bundle.

    /** The template store. */
    private TemplateStore fStore;

    /** The context type registry. */
    private ContributionContextTypeRegistry fRegistry = null;

    /** Key to store custom templates. */
    private static final String CUSTOM_TEMPLATES_PY_KEY = "org.python.pydev.editor.templates.PyTemplatePreferencesPage";

    public static final String DEFAULT_PYDEV_SCOPE = "org.python.pydev";


    /**
     * The constructor.
     */
    public PydevPlugin() {
        super();
        plugin = this;
    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
        try {
            resourceBundle = ResourceBundle.getBundle("org.python.pydev.PyDevPluginResources");
        } catch (MissingResourceException x) {
            resourceBundle = null;
        }
        Preferences preferences = plugin.getPluginPreferences();
        preferences.addPropertyChangeListener(this);
        setPythonInterpreterManager(new PythonInterpreterManager(preferences));
        setJythonInterpreterManager(new JythonInterpreterManager(preferences));
        

        //restore the nature for all python projects
        new Job("PyDev: Restoring projects python nature"){

            protected IStatus run(IProgressMonitor monitor) {
                IProject[] projects = getWorkspace().getRoot().getProjects();
                for (int i = 0; i < projects.length; i++) {
                    IProject project = projects[i];
                    try {
                        if (project.isOpen() && project.hasNature(PythonNature.PYTHON_NATURE_ID)) {
                            PythonNature.addNature(project, monitor);
                        }
                    } catch (Exception e) {
                        PydevPlugin.log(e);
                    }
                }
                return Status.OK_STATUS;
            }
            
        }.schedule();
        
    }
    

    /**
     * This is called when the plugin is being stopped.
     * 
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(BundleContext context) throws Exception {
        
        try {
            //stop the running shells
            AbstractShell.shutdownAllShells();

            Preferences preferences = plugin.getPluginPreferences();
            preferences.removePropertyChangeListener(this);
            
            //save the natures (code completion stuff).
            IProject[] projects = getWorkspace().getRoot().getProjects();
            for (int i = 0; i < projects.length; i++) {
                try {
                    IProject project = projects[i];
                    if (project.isOpen()){
	                    IProjectNature n = project.getNature(PythonNature.PYTHON_NATURE_ID);
	                    if(n instanceof PythonNature){
	                        PythonNature nature = (PythonNature) n;
	                        nature.saveAstManager();
	                    }
                    }
                } catch (CoreException e) {
                    PydevPlugin.log(e);
                }
            }

        } finally{
	        super.stop(context);
        }
    }

    public static PydevPlugin getDefault() {
        return plugin;
    }

    public static String getPluginID() {
        return PydevPlugin.getBundleInfo().getPluginID();
    }

    /**
     * Returns the workspace instance.
     */
    public static IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    public static Status makeStatus(int errorLevel, String message, Throwable e) {
        return new Status(errorLevel, getPluginID(), errorLevel, message, e);
    }

    /**
     * Returns the string from the plugin's resource bundle, or 'key' if not found.
     */
    public static String getResourceString(String key) {
        ResourceBundle bundle = plugin.getResourceBundle();
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
    
    
    public void propertyChange(Preferences.PropertyChangeEvent event) {
        //		System.out.println( event.getProperty()
        //		 + "\n\told setting: "
        //		 + event.getOldValue()
        //		 + "\n\tnew setting: "
        //		 + event.getNewValue());
    }

    public static void log(String message, Throwable e) {
        log(IStatus.ERROR, message, e);
    }
    
    public static void log(int errorLevel, String message, Throwable e) {
        log(errorLevel, message, e, true);
    }
    public static void log(String message, Throwable e, boolean printToConsole) {
        log(IStatus.ERROR, message, e, printToConsole);
    }

    public static void logInfo(Exception e) {
        log(IStatus.INFO, e.getMessage(), e, true);
	}

    /**
     * @param errorLevel IStatus.[OK|INFO|WARNING|ERROR]
     */
    public static void log(int errorLevel, String message, Throwable e, boolean printToConsole) {
        if(printToConsole){
        	if(errorLevel == IStatus.ERROR){
        		System.out.println("Error received...");
        	}else{
        		System.out.println("Log received...");
        	}
            System.out.println(message);
            System.err.println(message);
            if(e != null){
            	e.printStackTrace();
            }
        }
        
        try {
	        Status s = new Status(errorLevel, getPluginID(), errorLevel, message, e);
	        getDefault().getLog().log(s);
        } catch (Exception e1) {
            //logging should not fail!
        }
    }

    public static void log(Throwable e) {
        log(e, true);
    }
    
    public static void log(Throwable e, boolean printToConsole) {
        log(IStatus.ERROR, e.getMessage() != null ? e.getMessage() : "No message gotten.", e, printToConsole);
    }

    public static void logInfo(String msg) {
        IStatus s = PydevPlugin.makeStatus(IStatus.INFO, msg, null);
        getDefault().getLog().log(s);
    }
    
    public static CoreException log(String msg) {
        IStatus s = PydevPlugin.makeStatus(IStatus.ERROR, msg, new RuntimeException(msg));
        CoreException e = new CoreException(s);
        PydevPlugin.log(e);
        return e;
    }

    /**
     *  
     */
    public static IPath getPath(IPath location) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();

        IFile[] files = root.findFilesForLocation(location);
        IContainer[] folders =  root.findContainersForLocation(location);
        if (files.length > 0) {
            for (int i = 0; i < files.length; i++)
                return files[i].getProjectRelativePath();
        } else {
            for (int i = 0; i < folders.length; i++)
                return folders[i].getProjectRelativePath();
        }

        return null;
    }

    public static IPath getLocationFromWorkspace(IPath path) {
        return getLocationFromWorkspace(path, 0);
    }
    /**
     * This one should only be used if the root (project) is unknown.
     * 
     * @see PydevPlugin.getLocation#IPath, IContainer
     * @param path
     * @return
     */
    public static IPath getLocationFromWorkspace(IPath path, int repetitions) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IContainer root = workspace.getRoot();
        repetitions++;
        return getLocation(path, root, repetitions);
    }

    public static IPath getLocation(IPath path, IContainer root) {
        return getLocation(path, root, 0);
    }
    
    /**
     * Returns the location in the filesystem for the given path
     * 
     * @param path
     * @param root the path must be inside this root
     * @return
     */
    public static IPath getLocation(IPath path, IContainer root, int repetitions) {
        if(repetitions > 3){
            return null;
        }
        repetitions++;
        IResource resource = root.findMember(path);
        IPath location = null;
        if (resource != null) {
            location = resource.getLocation();
        }
        
        if(location == null){
            location = getLocationFromWorkspace(path, repetitions);
        }
        return location;
    }
    /**
     * Utility function that opens an editor on a given path.
     * 
     * @return part that is the editor
     */
    public static IEditorPart doOpenEditor(IPath path, boolean activate) {
        if (path == null)
            return null;

        try {
            
            IEditorInput file = createEditorInput(path);
	        
	        final IWorkbench workbench = plugin.getWorkbench();
	        if(workbench == null){
	        	throw new RuntimeException("workbench cannot be null");
	        }

	        IWorkbenchWindow activeWorkbenchWindow = workbench.getActiveWorkbenchWindow();
	        if(activeWorkbenchWindow == null){
	        	throw new RuntimeException("activeWorkbenchWindow cannot be null (we have to be in a ui thread for this to work)");
	        }
	        
			IWorkbenchPage wp = activeWorkbenchWindow.getActivePage();
        
            // File is inside the workspace
            return IDE.openEditor(wp, file, PyEdit.EDITOR_ID);
            
        } catch (Exception e) {
            log(IStatus.ERROR, "Unexpected error opening path " + path.toString(), e);
            return null;
        }
    }

    
    
    

//  =====================
//  ===================== ALL BELOW IS COPIED FROM org.eclipse.ui.internal.editors.text.OpenExternalFileAction
//  =====================

    /**
     * @param path
     * @return
     */
    public static IEditorInput createEditorInput(IPath path) {
        IEditorInput edInput;
        IWorkspace w = ResourcesPlugin.getWorkspace();      
        IFile file = w.getRoot().getFileForLocation(path);
        if (file == null  || !file.exists()){
            //it is probably an external file
            File file2 = path.toFile();
            edInput = createEditorInput(file2);
        }else{
            edInput = new FileEditorInput(file);
        }
        return edInput;
    }

    private static IEditorInput createEditorInput(File file) {
        IFile[] workspaceFile= getWorkspaceFile(file);
        if (workspaceFile != null && workspaceFile.length > 0)
            return new FileEditorInput(workspaceFile[0]);
        return new PydevFileEditorInput(file);
    }

    public static IFile[] getWorkspaceFile(File file) {
        IWorkspace workspace= ResourcesPlugin.getWorkspace();
        IPath location= Path.fromOSString(file.getAbsolutePath());
        IFile[] files= workspace.getRoot().findFilesForLocation(location);
        files= filterNonExistentFiles(files);
        if (files == null || files.length == 0){
            return null;
        }
        
        return files;
        //we are out of the loop from the interface when this is called
//        if (files.length == 1)
//            return files[0];
//        return selectWorkspaceFile(files);
    }
    

    private static IFile[] filterNonExistentFiles(IFile[] files){
        if (files == null)
            return null;

        int length= files.length;
        ArrayList<IFile> existentFiles= new ArrayList<IFile>(length);
        for (int i= 0; i < length; i++) {
            if (files[i].exists())
                existentFiles.add(files[i]);
        }
        return (IFile[])existentFiles.toArray(new IFile[existentFiles.size()]);
    }
//    private IFile selectWorkspaceFile(IFile[] files) {
//        ElementListSelectionDialog dialog= new ElementListSelectionDialog(fWindow.getShell(), new FileLabelProvider());
//        dialog.setElements(files);
//        dialog.setTitle(TextEditorMessages.OpenExternalFileAction_title_selectWorkspaceFile);
//        dialog.setMessage(TextEditorMessages.OpenExternalFileAction_message_fileLinkedToMultiple);
//        if (dialog.open() == Window.OK)
//            return (IFile) dialog.getFirstResult();
//        return null;
//    }

    
//  =====================
//  ===================== END COPY FROM org.eclipse.ui.internal.editors.text.OpenExternalFileAction
//  =====================


    /**
	 * Returns this plug-in's template store.
	 * 
	 * @return the template store of this plug-in instance
	 */
    public TemplateStore getTemplateStore() {
        if (fStore == null) {
            fStore = new ContributionTemplateStore(getContextTypeRegistry(), getPreferenceStore(), CUSTOM_TEMPLATES_PY_KEY);
            try {
                fStore.load();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return fStore;
    }

    /**
     * Returns this plug-in's context type registry.
     * 
     * @return the context type registry for this plug-in instance
     */
    public ContextTypeRegistry getContextTypeRegistry() {
        if (fRegistry == null) {
            // create an configure the contexts available in the template editor
            fRegistry = new ContributionContextTypeRegistry();
            fRegistry.addContextType(PyContextType.PY_CONTEXT_TYPE);
        }
        return fRegistry;
    }

    
    
    /**
     * 
     * @return the script to get the variables.
     * 
     * @throws CoreException
     */
    public static File getScriptWithinPySrc(String targetExec) throws CoreException {
        IPath relative = new Path("PySrc").addTrailingSeparator().append(targetExec);
        return getRelativePath(relative);
    }

    /**
     * @param relative
     * @return
     * @throws CoreException
     */
    public static File getRelativePath(IPath relative) throws CoreException {
        return PydevPlugin.getBundleInfo().getRelativePath(relative);
    }
    
    public static ImageCache getImageCache(){
        return PydevPlugin.getBundleInfo().getImageCache();
    }
    

    public static File getImageWithinIcons(String icon) throws CoreException {

        IPath relative = new Path("icons").addTrailingSeparator().append(icon);

        return getRelativePath(relative);

    }

    /**
     * Returns the directories and python files in a list.
     * 
     * @param file
     * @return tuple with files in pos 0 and folders in pos 1
     */
    public static List<File>[] getPyFilesBelow(File file, IProgressMonitor monitor, final boolean includeDirs) {
        return getPyFilesBelow(file, monitor, true, true);
    }
    /**
     * Returns the directories and python files in a list.
     * 
     * @param file
     * @return tuple with files in pos 0 and folders in pos 1
     */
    public static List<File>[] getPyFilesBelow(File file, IProgressMonitor monitor, final boolean includeDirs, boolean checkHasInit) {
        FileFilter filter = new FileFilter() {
    
            public boolean accept(File pathname) {
                if (includeDirs)
                    return pathname.isDirectory() || pathname.toString().endsWith(".py");
                else
                    return pathname.isDirectory() == false && pathname.toString().endsWith(".py");
            }
    
        };
        return getPyFilesBelow(file, filter, monitor, true, checkHasInit);
    }


    public static List<File>[] getPyFilesBelow(File file, FileFilter filter, IProgressMonitor monitor, boolean checkHasInit) {
        return getPyFilesBelow(file, filter, monitor, true, checkHasInit);
    }
    
    public static List<File>[] getPyFilesBelow(File file, FileFilter filter, IProgressMonitor monitor, boolean addSubFolders, boolean checkHasInit) {
        return getPyFilesBelow(file, filter, monitor, addSubFolders, 0, checkHasInit);
    }
    /**
     * Returns the directories and python files in a list.
     * 
     * @param file
     * @param addSubFolders: indicates if sub-folders should be added
     * @return tuple with files in pos 0 and folders in pos 1
     */
    private static List<File>[] getPyFilesBelow(File file, FileFilter filter, IProgressMonitor monitor, boolean addSubFolders, int level, boolean checkHasInit) {
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        List<File> filesToReturn = new ArrayList<File>();
        List<File> folders = new ArrayList<File>();

        if (file.exists() == true) {

            if (file.isDirectory()) {
                File[] files = null;

                if (filter != null) {
                    files = file.listFiles(filter);
                } else {
                    files = file.listFiles();
                }

                boolean hasInit = false;

                List<File> foldersLater = new LinkedList<File>();
                
                for (int i = 0; i < files.length; i++) {
                    File file2 = files[i];
                    
                    if(file2.isFile()){
	                    filesToReturn.add(file2);
	                    monitor.worked(1);
	                    monitor.setTaskName("Found:" + file2.toString());
	                    
                        if (checkHasInit){
    	                    if(file2.getName().equals("__init__.py")){
    	                        hasInit = true;
    	                    }
                        }
	                    
                    }else{
                        foldersLater.add(file2);
                    }
                }
                
                if(!checkHasInit  || hasInit || level == 0){
	                folders.add(file);

	                for (Iterator iter = foldersLater.iterator(); iter.hasNext();) {
	                    File file2 = (File) iter.next();
	                    if(file2.isDirectory() && addSubFolders){
		                    List<File>[] below = getPyFilesBelow(file2, filter, monitor, addSubFolders, level+1, checkHasInit);
		                    filesToReturn.addAll(below[0]);
		                    folders.addAll(below[1]);
		                    monitor.worked(1);
	                    }
	                }
                }

                
            } else if (file.isFile()) {
                filesToReturn.add(file);
                
            } else{
                throw new RuntimeException("Not dir nor file... what is it?");
            }
        }
        
        return new List[] { filesToReturn, folders };

    }

    


    //PyUnit integration
    
	/** Listener list **/
    private List listeners = new ArrayList();


	public void addTestListener(ITestRunListener listener) {
		listeners.add(listener);
	}
	
	public void removeTestListener(ITestRunListener listener) {
		listeners.remove(listener);
	}

	public List getListeners() {
		return listeners;
	}
	
	public void runTests(String moduleDir, String moduleName, IProject project) throws IOException, CoreException {
		new PyUnitTestRunner().runTests(moduleDir, moduleName, project);
	}
	
	public void fireTestsStarted(int count) {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testsStarted(count);
		}
	}

	public void fireTestsFinished() {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testsFinished();
		}
	}

	public void fireTestStarted(String klass, String methodName) {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testStarted(klass, methodName);
		}
	}

	public void fireTestFailed(String klass, String methodName, String trace) {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testFailed(klass, methodName, trace);
		}
	}
    /**
     * @param file the file we want to get info on.
     * @return a tuple with the pythonnature to be used and the name of the module represented by the file in that scenario.
     */
    public static Tuple<SystemPythonNature, String> getInfoForFile(File file){
        String modName = null;
        IInterpreterManager pythonInterpreterManager = getPythonInterpreterManager();
        IInterpreterManager jythonInterpreterManager = getJythonInterpreterManager();
    
        SystemPythonNature systemPythonNature = new SystemPythonNature(pythonInterpreterManager);
        SystemPythonNature pySystemPythonNature = systemPythonNature;
        SystemPythonNature jySystemPythonNature = null;
        try {
            modName = systemPythonNature.resolveModule(file);
        } catch (Exception e) {
            // that's ok
        }
        if(modName == null){
            systemPythonNature = new SystemPythonNature(jythonInterpreterManager);
            jySystemPythonNature = systemPythonNature;
            try {
                modName = systemPythonNature.resolveModule(file);
            } catch (Exception e) {
                // that's ok
            }
        }
        if(modName != null){
            return new Tuple<SystemPythonNature, String>(systemPythonNature, modName);
        }else{
            //unable to discover it
            try {
                // the default one is python (actually, this should never happen, but who knows)
                pythonInterpreterManager.getDefaultInterpreter();
                modName = getModNameFromFile(file);
                return new Tuple<SystemPythonNature, String>(pySystemPythonNature, modName);
            } catch (Exception e) {
                //the python interpreter manager is not valid or not configured
                try {
                    // the default one is jython
                    jythonInterpreterManager.getDefaultInterpreter();
                    modName = getModNameFromFile(file);
                    return new Tuple<SystemPythonNature, String>(jySystemPythonNature, modName);
                } catch (Exception e1) {
                    // ok, nothing to do about it, no interpreter is configured
                    return null;
                }
            }
        }
    }
    
    /**
     * This is the last resort (should not be used anywhere else).
     */
    private static String getModNameFromFile(File file) {
        if(file == null){
            return null;
        }
        String name = file.getName();
        int i = name.indexOf('.');
        if (i != -1){
            return name.substring(0, i);
        }
        return name;
    }

    /**
     * @return a preference store that has the pydev preference store and the default editors text store
     */
    public static IPreferenceStore getChainedPrefStore() {
        IPreferenceStore general = EditorsUI.getPreferenceStore();
        IPreferenceStore preferenceStore = getDefault().getPreferenceStore();
        ChainedPreferenceStore store = new ChainedPreferenceStore(new IPreferenceStore[] { general, preferenceStore });
        return store;
    }
    
    public static String getIResourceOSString(IResource f) {
        String fullPath = f.getRawLocation().toOSString();
        //now, we have to make sure it is canonical...
        File file = new File(fullPath);
        if(file.exists()){
            return REF.getFileAbsolutePath(file);
        }else{
            //it does not exist, so, we have to check its project to validate the part that we can
            IProject project = f.getProject();
            IPath location = project.getLocation();
            File projectFile = location.toFile();
            if(projectFile.exists()){
                String projectFilePath = REF.getFileAbsolutePath(projectFile);
                
                if(fullPath.startsWith(projectFilePath)){
                    //the case is all ok
                    return fullPath;
                }else{
                    //the case appears to be different, so, let's check if this is it...
                    if(fullPath.toLowerCase().startsWith(projectFilePath.toLowerCase())){
                        String relativePart = fullPath.substring(projectFilePath.length());
                        
                        //at least the first part was correct
                        return projectFilePath+relativePart;
                    }
                }
            }
        }
        
        //it may not be correct, but it was the best we could do...
        return fullPath;
    }
    
    public static void writeToPlatformFile(Object obj, String fileName) {
        Bundle bundle = Platform.getBundle("org.python.pydev");
        IPath path = Platform.getStateLocation( bundle );       
        path = path.addTrailingSeparator();
        path = path.append(fileName);
        try {
            FileOutputStream out = new FileOutputStream(path.toFile());
            REF.writeToStreamAndCloseIt(obj, out);
            
        } catch (Exception e) {
            PydevPlugin.log(e);
            throw new RuntimeException(e);
        }               
    }

    public static Object readFromPlatformFile(String fileName) {
        Bundle bundle = Platform.getBundle("org.python.pydev");
        IPath path = Platform.getStateLocation( bundle );       
        path = path.addTrailingSeparator();
        path = path.append(fileName);
        
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(path.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return REF.readFromInputStreamAndCloseIt(new ICallback<Object, ObjectInputStream>(){

            public Object call(ObjectInputStream arg) {
                try{
                    return arg.readObject();
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            }}, 
            
            fileInputStream);
    }
}