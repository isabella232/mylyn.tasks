/*******************************************************************************
 * Copyright (c) 2009, 2010 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *     Frank Becker - improvements
 *******************************************************************************/

package org.eclipse.mylyn.bugzilla.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.mylyn.bugzilla.tests.core.BugzillaClientTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaConfigurationTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaCustomFieldsTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaFlagsTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaRepositoryConnectorConfigurationTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaRepositoryConnectorStandaloneTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaTaskCompletionTest;
import org.eclipse.mylyn.bugzilla.tests.core.BugzillaVersionTest;
import org.eclipse.mylyn.bugzilla.tests.support.BugzillaFixture;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaVersion;

/**
 * @author Steffen Pingel
 * @author Thomas Ehrnhoefer
 * @author Frank Becker
 */
public class AllBugzillaHeadlessStandaloneTests {

	public static Test suite() {
		return suite(false);
	}

	public static Test suite(boolean defaultOnly) {
		TestSuite suite = new TestSuite(AllBugzillaHeadlessStandaloneTests.class.getName());
		// tests that only need to run once (i.e. no network i/o so doesn't matter which repository)
		suite.addTestSuite(BugzillaConfigurationTest.class);
		suite.addTestSuite(BugzillaVersionTest.class);
		suite.addTestSuite(BugzillaTaskCompletionTest.class);
		// tests that run against all repository versions
		if (defaultOnly) {
			addTests(suite, BugzillaFixture.DEFAULT);
		} else {
			for (BugzillaFixture fixture : BugzillaFixture.ALL) {
				addTests(suite, fixture);
			}
		}
		return suite;
	}

	protected static void addTests(TestSuite suite, BugzillaFixture fixture) {
		if (fixture.isExcluded()) {
			return;
		}

		fixture.createSuite(suite);
		// XXX: re-enable when webservice is used for retrieval of history
		// fixture.add(fixtureSuite, BugzillaTaskHistoryTest.class); 
		fixture.add(BugzillaRepositoryConnectorStandaloneTest.class);
		fixture.add(BugzillaRepositoryConnectorConfigurationTest.class);

		// Move any tests here that are resulting in spurious failures
		// due to recent changes in Bugzilla Server head.
		if (fixture != BugzillaFixture.BUGS_HEAD) {
			fixture.add(BugzillaClientTest.class);

			// Only run these tests on > 3.2 repositories
			if (!fixture.getBugzillaVersion().isSmallerOrEquals(BugzillaVersion.BUGZILLA_3_2)) {
				fixture.add(BugzillaCustomFieldsTest.class);
				fixture.add(BugzillaFlagsTest.class);
			}
		}
		fixture.done();
	}

}
