/*******************************************************************************
 * Copyright (c) 2003 - 2006 University Of British Columbia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     University Of British Columbia - initial API and implementation
 *******************************************************************************/
package org.eclipse.mylar.internal.bugzilla.ui.wizard;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylar.internal.bugzilla.ui.BugzillaUiPlugin;
import org.eclipse.mylar.internal.bugzilla.ui.editor.NewBugEditorInput;
import org.eclipse.mylar.internal.tasklist.ui.TaskUiUtil;
import org.eclipse.mylar.provisional.tasklist.TaskRepository;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * @author Mik Kersten
 * @author Rob Elves
 */
public class NewBugzillaReportWizard extends AbstractBugzillaReportWizard {

	private static final String TITLE = "New Bugzilla Task";

	private final TaskRepository repository;

	private final BugzillaProductPage productPage;

	public NewBugzillaReportWizard(TaskRepository repository) {
		this(false, repository);
		super.setWindowTitle(TITLE);
	}

	public NewBugzillaReportWizard(boolean fromDialog, TaskRepository repository) {
		super(repository);
		this.repository = repository;
		this.fromDialog = fromDialog;
		this.productPage = new BugzillaProductPage(workbenchInstance, this, repository);
	}

	@Override
	public void addPages() {
		super.addPages();
		addPage(productPage);

	}

	@Override
	public boolean canFinish() {
		return completed;
	}

	@Override
	protected void saveBugOffline() {
		// AbstractRepositoryConnector client = (AbstractRepositoryConnector)
		// MylarTaskListPlugin.getRepositoryManager()
		// .getRepositoryConnector(BugzillaPlugin.REPOSITORY_KIND);
		// client.saveOffline(model);
	}

	@Override
	protected AbstractBugzillaWizardPage getWizardDataPage() {
		return null;
	}

	@Override
	public boolean performFinish() {

		try {
			productPage.saveDataToModel();
			NewBugEditorInput editorInput = new NewBugEditorInput(repository, model);
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			TaskUiUtil.openEditor(editorInput, BugzillaUiPlugin.NEW_BUG_EDITOR_ID, page);
			return true;
		} catch (Exception e) {
			productPage.applyToStatusLine(new Status(IStatus.ERROR, "not_used", 0,
					"Problem occured retrieving repository configuration from " + repository.getUrl(), null));
		}
		return false;
	}
}

// Open new bug editor

// if (super.performFinish()) {
//
// String bugIdString = this.getId();
// int bugId = -1;
// // boolean validId = false;
// try {
// if (bugIdString != null) {
// bugId = Integer.parseInt(bugIdString);
// // validId = true;
// }
// } catch (NumberFormatException nfe) {
// MessageDialog.openError(null, IBugzillaConstants.TITLE_MESSAGE_DIALOG,
// "Could not create bug id, no valid id");
// return false;
// }
// // if (!validId) {
// // MessageDialog.openError(null,
// // IBugzillaConstants.TITLE_MESSAGE_DIALOG,
// // "Could not create bug id, no valid id");
// // return false;
// // }
//
// BugzillaTask newTask = new
// BugzillaTask(AbstractRepositoryTask.getHandle(repository.getUrl(), bugId),
// "<bugzilla info>", true);
// Object selectedObject = null;
// if (TaskListView.getFromActivePerspective() != null)
// selectedObject = ((IStructuredSelection)
// TaskListView.getFromActivePerspective().getViewer()
// .getSelection()).getFirstElement();
//
// // MylarTaskListPlugin.getTaskListManager().getTaskList().addTask(newTask);
//
// if (selectedObject instanceof TaskCategory) {
// MylarTaskListPlugin.getTaskListManager().getTaskList()
// .addTask(newTask, ((TaskCategory) selectedObject));
// } else {
// MylarTaskListPlugin.getTaskListManager().getTaskList().addTask(newTask,
// MylarTaskListPlugin.getTaskListManager().getTaskList().getRootCategory());
// }
//
// TaskUiUtil.refreshAndOpenTaskListElement(newTask);
// MylarTaskListPlugin.getSynchronizationManager().synchNow(0);
//
// return true;
// }
