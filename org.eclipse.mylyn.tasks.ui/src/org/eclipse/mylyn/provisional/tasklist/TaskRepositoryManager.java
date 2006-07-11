/*******************************************************************************
 * Copyright (c) 2004 - 2006 University Of British Columbia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     University Of British Columbia - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.provisional.tasklist;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.mylar.internal.core.MylarContextManager;
import org.eclipse.mylar.internal.core.util.MylarStatusHandler;
import org.eclipse.mylar.internal.tasklist.util.TaskRepositoriesExternalizer;
import org.eclipse.mylar.provisional.core.MylarPlugin;

/**
 * @author Mik Kersten
 * @author Rob Elves
 */
public class TaskRepositoryManager {

	public static final String OLD_PROPERTY_SYNCTIME = "synctime";

	// public static final String PROPERTY_SYNCTIMESTAMP = "synctimestamp";

	public static final String PROPERTY_SYNCTIMESTAMP = "lastsynctimestamp";

	public static final String PROPERTY_TIMEZONE = "timezone";

	public static final String PROPERTY_ENCODING = "encoding";

	public static final String PROPERTY_VERSION = "version";

	public static final String PROPERTY_KIND = "kind";

	public static final String PROPERTY_URL = "url";

	public static final String PROPERTY_DELIM = ":";

	public static final String PROPERTY_MIGRATION060 = "migration060";

	public static final String PREF_REPOSITORIES = "org.eclipse.mylar.tasklist.repositories.";

	private Map<String, AbstractRepositoryConnector> repositoryConnectors = new HashMap<String, AbstractRepositoryConnector>();

	private Map<String, Set<TaskRepository>> repositoryMap = new HashMap<String, Set<TaskRepository>>();

	private Set<ITaskRepositoryListener> listeners = new HashSet<ITaskRepositoryListener>();

	private Set<TaskRepository> orphanedRepositories = new HashSet<TaskRepository>();

	public static final String MESSAGE_NO_REPOSITORY = "No repository available, please add one using the Task Repositories view.";

	public static final String PREFIX_LOCAL_OLD = "task-";

	public static final String PREFIX_LOCAL = "local-";

	public static final String PREFIX_REPOSITORY_OLD = "Bugzilla";

	public static final String MISSING_REPOSITORY_HANDLE = "norepository" + MylarContextManager.CONTEXT_HANDLE_DELIM;

	private static final String PREF_STORE_DELIM = ", ";

	private TaskRepositoriesExternalizer externalizer = new TaskRepositoriesExternalizer();

	public Collection<AbstractRepositoryConnector> getRepositoryConnectors() {
		return Collections.unmodifiableCollection(repositoryConnectors.values());
	}

	public AbstractRepositoryConnector getRepositoryConnector(String kind) {
		return repositoryConnectors.get(kind);
	}

	public void addRepositoryConnector(AbstractRepositoryConnector repositoryConnector) {
		if (!repositoryConnectors.values().contains(repositoryConnector)) {
			repositoryConnectors.put(repositoryConnector.getRepositoryType(), repositoryConnector);
		}
	}

	public void addRepository(TaskRepository repository) {
		Set<TaskRepository> repositories;
		if (!repositoryMap.containsKey(repository.getKind())) {
			repositories = new HashSet<TaskRepository>();
			repositoryMap.put(repository.getKind(), repositories);
		} else {
			repositories = repositoryMap.get(repository.getKind());
		}
		repositories.add(repository);
		saveRepositories();
		for (ITaskRepositoryListener listener : listeners) {
			listener.repositoryAdded(repository);
		}
	}

	public void removeRepository(TaskRepository repository) {
		Set<TaskRepository> repositories = repositoryMap.get(repository.getKind());
		if (repositories != null) {
			repository.flushAuthenticationCredentials();
			repositories.remove(repository);
		}
		saveRepositories();
		for (ITaskRepositoryListener listener : listeners) {
			listener.repositoryRemoved(repository);
		}
	}

	public void addListener(ITaskRepositoryListener listener) {
		listeners.add(listener);
	}

	public void removeListener(ITaskRepositoryListener listener) {
		listeners.remove(listener);
	}

	public TaskRepository getRepository(String kind, String urlString) {
		if (repositoryMap.containsKey(kind)) {
			for (TaskRepository repository : repositoryMap.get(kind)) {
				if (repository.getUrl().equals(urlString)) {
					return repository;
				}
			}
		}
		return null;
	}

	/**
	 * @return the first connector to accept the URL
	 */
	public AbstractRepositoryConnector getRepositoryForTaskUrl(String url) {
		for (AbstractRepositoryConnector connector : getRepositoryConnectors()) {
			if (connector.getRepositoryUrlFromTaskUrl(url) != null) {
				return connector;
			}
		}
		return null;
	}

	public Set<TaskRepository> getRepositories(String kind) {
		if (repositoryMap.containsKey(kind)) {
			return repositoryMap.get(kind);
		} else {
			return Collections.emptySet();
		}
	}

	public List<TaskRepository> getAllRepositories() {
		List<TaskRepository> repositories = new ArrayList<TaskRepository>();
		for (AbstractRepositoryConnector repositoryConnector : repositoryConnectors.values()) {
			if (repositoryMap.containsKey(repositoryConnector.getRepositoryType())) {
				repositories.addAll(repositoryMap.get(repositoryConnector.getRepositoryType()));
			}
		}
		return repositories;
	}

	public TaskRepository getRepositoryForActiveTask(String repositoryKind) {
		List<ITask> activeTasks = MylarTaskListPlugin.getTaskListManager().getTaskList().getActiveTasks();
		if (activeTasks.size() == 1) {
			ITask activeTask = activeTasks.get(0);
			if (activeTask instanceof AbstractRepositoryTask) {
				String repositoryUrl = AbstractRepositoryTask.getRepositoryUrl(activeTask.getHandleIdentifier());
				for (TaskRepository repository : getRepositories(repositoryKind)) {
					if (repository.getUrl().equals(repositoryUrl)) {
						return repository;
					}
				}
			}
		}
		return null;
	}

	/**
	 * TODO: implement default support, this just returns first found
	 */
	public TaskRepository getDefaultRepository(String kind) {
		// HACK: returns first repository found
		if (repositoryMap.containsKey(kind)) {
			for (TaskRepository repository : repositoryMap.get(kind)) {
				return repository;
			}
		} else {
			Collection values = repositoryMap.values();
			if (!values.isEmpty()) {
				HashSet repoistorySet = (HashSet) values.iterator().next();
				return (TaskRepository) repoistorySet.iterator().next();
			}
		}
		return null;
	}

	public Map<String, Set<TaskRepository>> readRepositories() {
		
		repositoryMap.clear();
		orphanedRepositories.clear();
		
		boolean migrated = MylarTaskListPlugin.getMylarCorePrefs().getBoolean(PROPERTY_MIGRATION060);
		if (!migrated) {
			for (AbstractRepositoryConnector repositoryConnector : repositoryConnectors.values()) {
				String read = MylarTaskListPlugin.getMylarCorePrefs().getString(
						PREF_REPOSITORIES + repositoryConnector.getRepositoryType());
				Set<TaskRepository> repositories = new HashSet<TaskRepository>();
				if (read != null) {
					StringTokenizer st = new StringTokenizer(read, PREF_STORE_DELIM);
					while (st.hasMoreTokens()) {
						String urlString = st.nextToken();

						repositoryMap.put(repositoryConnector.getRepositoryType(), repositories);

						Map<String, String> properties = new HashMap<String, String>();
						for (String propertyName : repositoryConnector.repositoryPropertyNames()) {
							String key = urlString + PROPERTY_DELIM + propertyName;
							String value = MylarTaskListPlugin.getMylarCorePrefs().getString(key);
							properties.put(propertyName, value);
						}

						String prefIdSyncTime = urlString + PROPERTY_DELIM + PROPERTY_SYNCTIMESTAMP;
						String time = MylarTaskListPlugin.getMylarCorePrefs().getString(prefIdSyncTime);

						TaskRepository repository = new TaskRepository(repositoryConnector.getRepositoryType(),
								urlString, properties);
						if (!time.equals("")) {
							repository.setSyncTimeStamp(time);
						} else {
							// migrate to new time stamp 0.5.3 -> 0.6.0
							if (repository.getKind().equals("bugzilla")) {
								String oldSyncTimeId = urlString + PROPERTY_DELIM + OLD_PROPERTY_SYNCTIME;
								Long oldSyncTime = MylarTaskListPlugin.getMylarCorePrefs().getLong(oldSyncTimeId);
								if (oldSyncTime != 0L) {
									time = migrateOldBugzillaSyncDate(oldSyncTime);
									if (time != null && !time.equals("")) {
										repository.setSyncTimeStamp(time);
										MylarTaskListPlugin.getMylarCorePrefs().setValue(prefIdSyncTime, time);
									}
								}
							}
						}

						repositories.add(repository);
					}
				}
			}
			if(saveRepositories()) {
				MylarTaskListPlugin.getMylarCorePrefs().setValue(PROPERTY_MIGRATION060, true);
			}			
		} else {
			loadRepositories();
		}
		for (ITaskRepositoryListener listener : listeners) {
			listener.repositoriesRead();
		}
		return repositoryMap;
	}

	private void loadRepositories() {
		try {
			String dataDirectory = MylarPlugin.getDefault().getDataDirectory();
			File repositoriesFile = new File(dataDirectory + File.separator + MylarTaskListPlugin.DEFAULT_REPOSITORIES_FILE);
			// Will only load repositories for which a connector exists
			for (AbstractRepositoryConnector repositoryConnector : repositoryConnectors.values()) {
				repositoryMap.put(repositoryConnector.getRepositoryType(), new HashSet<TaskRepository>());
			}
			if (repositoriesFile.exists()) {
				Set<TaskRepository> repositories = externalizer.readRepositoriesFromXML(repositoriesFile);
				if (repositories != null && repositories.size() > 0) {
					for (TaskRepository repository : repositories) {
						if (repositoryMap.containsKey(repository.getKind())) {
							repositoryMap.get(repository.getKind()).add(repository);
						} else {
							orphanedRepositories.add(repository);
						}
					}
				}
			}
		} catch (Throwable t) {
			MylarStatusHandler.fail(t, "could not load repositories", false);
		}
	}

	// migrate to new time stamp 0.5.3 -> 0.6.0
	private String migrateOldBugzillaSyncDate(long oldTime) {
		String newDate = "";
		String DATE_FORMAT_2 = "yyyy-MM-dd HH:mm:ss";
		try {
			SimpleDateFormat delta_ts_format = new SimpleDateFormat(DATE_FORMAT_2);
			newDate = delta_ts_format.format(new Date(oldTime));
		} catch (Exception e) {
			// ignore
		}
		return newDate;
	}

	/**
	 * for testing purposes
	 */
	public void setVersion(TaskRepository repository, String version) {
		repository.setVersion(version);
		saveRepositories();
	}

	/**
	 * for testing purposes
	 */
	public void setEncoding(TaskRepository repository, String encoding) {
		repository.setCharacterEncoding(encoding);
		saveRepositories();
	}

	/**
	 * for testing purposes
	 */
	public void setTimeZoneId(TaskRepository repository, String timeZoneId) {
		repository.setTimeZoneId(timeZoneId);
		saveRepositories();
	}

	public void setSyncTime(TaskRepository repository, String syncTime) {
		repository.setSyncTimeStamp(syncTime);
		
		saveRepositories();
		

		// String prefIdSyncTime = repository.getUrl() + PROPERTY_DELIM +
		// PROPERTY_SYNCTIMESTAMP;
		// if (repository.getSyncTimeStamp() != null) {
		// MylarTaskListPlugin.getMylarCorePrefs().setValue(prefIdSyncTime,
		// repository.getSyncTimeStamp());
		//		}
	}

	public boolean saveRepositories() {
		Set<TaskRepository> repositoriesToWrite = new HashSet<TaskRepository>(getAllRepositories());
		// if for some reason a repository is added/changed to equal one in the
		// orphaned set the orphan is discarded
		for (TaskRepository repository : orphanedRepositories) {
			if (!repositoriesToWrite.contains(repository)) {
				repositoriesToWrite.add(repository);
			}
		}

		try {
			String dataDirectory = MylarPlugin.getDefault().getDataDirectory();
			File repositoriesFile = new File(dataDirectory + File.separator + MylarTaskListPlugin.DEFAULT_REPOSITORIES_FILE);
			externalizer.writeRepositoriesToXML(repositoriesToWrite, repositoriesFile);
		} catch (Throwable t) {
			MylarStatusHandler.fail(t, "could not save repositories", false);
			return false;
		}
		return true;
	}

	/**
	 * For testing.
	 */
	public void clearRepositories() {
		repositoryMap.clear();
		orphanedRepositories.clear();
		saveRepositories();
		// for (AbstractRepositoryConnector repositoryConnector :
		// repositoryConnectors.values()) {
		// String prefId = PREF_REPOSITORIES +
		// repositoryConnector.getRepositoryType();
		// MylarTaskListPlugin.getMylarCorePrefs().setValue(prefId, "");
		//		}
	}

	public void notifyRepositorySettingsChagned(TaskRepository repository) {
		for (ITaskRepositoryListener listener : listeners) {
			listener.repositorySettingsChanged(repository);
		}
	}
}
