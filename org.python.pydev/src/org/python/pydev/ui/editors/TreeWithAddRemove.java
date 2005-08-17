/*
 * Created on May 30, 2005
 *
 * @author Fabio Zadrozny
 */
package org.python.pydev.ui.editors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.dialogs.ResourceSelectionDialog;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.ui.UIConstants;
import org.python.pydev.ui.dialogs.ProjectFolderSelectionDialog;

/**
 * @author Fabio Zadrozny
 */
public class TreeWithAddRemove extends Composite{

    private Tree tree;
    private IProject project;

    /**
     * @param parent
     * @param style
     */
    public TreeWithAddRemove(Composite parent, int style, IProject project, String initialItems) {
        super(parent, style);
        if(initialItems == null){
            initialItems = "";
        }
        
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		this.setLayout(layout);

		GridData data= new GridData(GridData.FILL_BOTH);

	    this.project = project;
	    
	    tree = new Tree(this, SWT.BORDER );
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		tree.setLayoutData(data);

		Composite buttonsSourceFolders= new Composite(this, SWT.NONE);
		buttonsSourceFolders.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		
		layout = new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		buttonsSourceFolders.setLayout(layout);
		
		Button buttonAddSourceFolder = new Button(buttonsSourceFolders, SWT.PUSH);
		customizeAddSourceFolderButton(buttonAddSourceFolder, true);
        buttonAddSourceFolder.setText(getButtonAddText());
        data = new GridData ();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        buttonAddSourceFolder.setLayoutData(data);

        Button buttonAddJar = new Button(buttonsSourceFolders, SWT.PUSH);
        customizeAddSourceFolderButton(buttonAddJar, false);
        buttonAddJar.setText("Add jar");
        data = new GridData ();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        buttonAddJar.setLayoutData(data);
        
		Button buttonRemSourceFolder = new Button(buttonsSourceFolders, SWT.PUSH);
		customizeRemSourceFolderButton(buttonRemSourceFolder);
        data = new GridData ();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        buttonRemSourceFolder.setLayoutData(data);

        String[] its = getStrAsStrItems(initialItems);
        for (int i = 0; i < its.length; i++) {
            addTreeItem(its[i]);
        }

    }

    

	/**
	 * Remove is almost always default
	 * 
     * @param buttonRemSourceFolder
     */
    protected void customizeRemSourceFolderButton(Button buttonRem) {
        buttonRem.setText(getButtonRemoveText());
        buttonRem.setToolTipText("Remove the selected item");
        buttonRem.addSelectionListener(new SelectionListener(){

            public void widgetSelected(SelectionEvent e) {
                TreeItem[] selection = tree.getSelection();
                for (int i = 0; i < selection.length; i++) {
                    selection[i].dispose();
                }
            }

            public void widgetDefaultSelected(SelectionEvent e) {
            }
            
        });
    }


    /**
     * 
     * 
     * @param buttonAddSourceFolder
     */
    protected void customizeAddSourceFolderButton(Button buttonAddSourceFolder, final boolean chooseSourceFolder) {
        buttonAddSourceFolder.addSelectionListener(new SelectionListener(){

            public void widgetSelected(SelectionEvent e) {
                Object d;
                if(chooseSourceFolder){
                    d = getSelectionDialogAddSourceFolder();
                }else{
                    d = getSelectionDialogAddJar();
                }
                
                if(d instanceof FileDialog){
                    FileDialog dialog = (FileDialog) d;
                    String filePath = dialog.open();
                    addTreeItem(filePath);

                }else if(d instanceof DirectoryDialog){
                    DirectoryDialog dialog = (DirectoryDialog) d;
                    String filePath = dialog.open();
                    addTreeItem(filePath);
                    
                }else if(d instanceof SelectionDialog){
                    SelectionDialog dialog = (SelectionDialog) d;
	                dialog.open();
	                Object[] objects = dialog.getResult();
	                if (objects != null) { 
                        for (int i = 0; i < objects.length; i++) {
                            Object object = objects[i];
                            if (object instanceof IPath) {
                                IPath p = (IPath) object;
                                String pathAsString = getPathAsString(p);
                                addTreeItem(pathAsString);
                            }else if(object instanceof IFile){
                                IFile p = (IFile) object;
                                String pathAsString = getPathAsString(p.getProjectRelativePath());
                                if(pathAsString.endsWith(".jar") || pathAsString.endsWith(".zip")){
                                    addTreeItem(pathAsString);
                                }
                            }
                        }
	                }
                }else{
                    throw new RuntimeException("Dont know how to treat dialog: "+d.getClass());
                }

            }

            public void widgetDefaultSelected(SelectionEvent e) {
            }
            
        });
    }


    /**
     * @return
     */
    protected String getButtonRemoveText() {
        return "Remove";
    }


    /**
     * @return
     */
    protected String getButtonAddText() {
        return "Add source folder";
    }



    /**
     * @return
     */
    protected String getImageConstant() {
        return UIConstants.SOURCE_FOLDER_ICON;
    }


    
    /**
     * @return
     */
    protected Object getSelectionDialogAddJar() {
        return new ResourceSelectionDialog(getShell(), project, "Choose jars to add to PYTHONPATH");
    }

    /**
     * @return
     */
    protected Object getSelectionDialogAddSourceFolder() {
        return new ProjectFolderSelectionDialog(getShell(), project, true, "Choose source folders to add to PYTHONPATH");
    }



    /**
     * @param p
     * @return
     */
    protected String getPathAsString(IPath p) {
        return p.toString(); //default is just returning the code
    }



    /**
     * @param pathAsString
     */
    private void addTreeItem(String pathAsString) {
        if(pathAsString != null && pathAsString.trim().length() > 0){
	        TreeItem item = new TreeItem(tree, 0);
	        item.setText(pathAsString);
	        item.setImage(PydevPlugin.getImageCache().get(getImageConstant()));
        }
    }

    public String[] getStrAsStrItems(String str){
        return str.split("\\|");
    }
    public String getTreeItemsAsStr(){
        StringBuffer ret = new StringBuffer();
        TreeItem[] items = tree.getItems();
        for (int i = 0; i < items.length; i++) {
            String text = items[i].getText();
            if(text != null && text.trim().length() > 0){
	            ret.append(text);
	            ret.append("|");
            }
        }
        return ret.toString();
    }

}
