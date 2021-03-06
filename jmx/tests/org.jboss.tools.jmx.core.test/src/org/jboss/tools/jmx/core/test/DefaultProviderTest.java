/*******************************************************************************
 * Copyright (c) 2006 Jeff Mesnil
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    "Rob Stryker" <rob.stryker@redhat.com> - Initial implementation
 *******************************************************************************/
package org.jboss.tools.jmx.core.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.swt.widgets.Display;
import org.jboss.tools.jmx.core.ExtensionManager;
import org.jboss.tools.jmx.core.IConnectionProvider;
import org.jboss.tools.jmx.core.IConnectionWrapper;
import org.jboss.tools.jmx.core.providers.DefaultConnectionProvider;
import org.jboss.tools.jmx.core.test.util.TestProjectProvider;
import org.jboss.tools.jmx.core.tree.DomainNode;
import org.jboss.tools.jmx.core.tree.MBeansNode;
import org.jboss.tools.jmx.core.tree.Node;
import org.jboss.tools.jmx.core.tree.Root;
import org.jboss.tools.test.util.JobUtils;

public class DefaultProviderTest extends TestCase {
	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testExtensionExists() {
    	String providerClass = "org.jboss.tools.jmx.core.providers.DefaultConnectionProvider";
		IExtension[] extensions = findExtension(ExtensionManager.MBEAN_CONNECTION);
		for (int i = 0; i < extensions.length; i++) {
			IConfigurationElement elements[] = extensions[i]
					.getConfigurationElements();
			for( int j = 0; j < elements.length; j++ ) {
				if( elements[j].getAttribute("class").equals(providerClass))
					return;
			}
		}
		fail("Default Provider extension not found");
	}

	public void testProviderExists() throws Exception {
		IConnectionProvider defProvider = null;
		IConnectionProvider[] providers = ExtensionManager.getProviders();
		for( int i = 0; i < providers.length; i++ ) {
			if( providers[i].getId().equals(DefaultConnectionProvider.PROVIDER_ID))
				defProvider = providers[i];
		}
		if( defProvider == null )
			fail("Default Provider not found");

		defProvider = ExtensionManager.getProvider(DefaultConnectionProvider.PROVIDER_ID);
		if( defProvider == null )
			fail("Default Provider not found 2");

    }

	@SuppressWarnings("unchecked")
	public void testConnection() throws Exception {
		TestProjectProvider projectProvider;
		IProject project;
		projectProvider = new TestProjectProvider(JMXTestPlugin.PLUGIN_ID,
				"projects" + Path.SEPARATOR + "JMX_EXAMPLE",
				null, true);
		project = projectProvider.getProject();
		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		JobUtils.waitForIdle(500, 60000);
		
		final ILaunchConfigurationWorkingCopy wc = createLaunch();
		final ILaunch[] launch = new ILaunch[1];
		
		final CountDownLatch latch = new CountDownLatch(1);
		final Exception[] e = new Exception[1];
		Thread t = new Thread() {
			public void run() {
				System.out.println("Launching the mbean server");
				try {
					launch[0] = wc.launch("run", new NullProgressMonitor());
				} catch(CoreException ce) {
					System.out.println("MBean server launch failed");
					ce.printStackTrace();
					e[0] = ce;
					latch.countDown();
					return;
				}
				
				System.out.println("Launch succeeded");
				IProcess p = launch[0].getProcesses()[0];
				p.getStreamsProxy().getErrorStreamMonitor().addListener(new IStreamListener() {
					public void streamAppended(String text, IStreamMonitor monitor) {
						System.out.println("[error] " + text);
						latch.countDown();
					} 
				});
				p.getStreamsProxy().getOutputStreamMonitor().addListener(new IStreamListener() {
					public void streamAppended(String text, IStreamMonitor monitor) {
						System.out.println("[out] " + text);
						latch.countDown();
					} 
				});
			}
		};
		t.start();
		
		// Have to run the event loop, because it seems launching a launch config requires ui synchronization
		long complete = System.currentTimeMillis() + 60000;
		Display d = Display.getDefault();
		while( !d.isDisposed() && System.currentTimeMillis() < complete && latch.getCount() > 0) {
			 if (!d.readAndDispatch ())
		            d.sleep ();
		}
		
		assertTrue(latch.getCount() < 1);
		assertNull(e[0]);
		
		try {
			IConnectionProvider defProvider =
				ExtensionManager.getProvider(DefaultConnectionProvider.PROVIDER_ID);
			HashMap map = new HashMap();
			map.put(DefaultConnectionProvider.ID, "Test Connection");
			map.put(DefaultConnectionProvider.URL, "service:jmx:rmi:///jndi/rmi://localhost:9999" +
					"/jmxrmi");
			map.put(DefaultConnectionProvider.USERNAME, "");
			map.put(DefaultConnectionProvider.PASSWORD, "");
			IConnectionWrapper wrapper = defProvider.createConnection(map);
			assertTrue("Connection was null", wrapper != null);

			wrapper.connect();
			Root root = wrapper.getRoot();
			assertTrue("Root was not null", root == null);
			
			wrapper.loadRoot(new NullProgressMonitor());
			root = wrapper.getRoot();
			assertTrue("Root was null", root != null);
			
			Node[] children = root.getChildren();
			assertTrue("children were null", children != null);
			assertTrue("children length was less than 1", children.length >= 0);
			assertTrue("Mbeans node found", children[0] instanceof MBeansNode);
			
			children = children[0].getChildren();
			assertTrue("children were null", children != null);
			assertTrue("children length was less than 1", children.length >= 0);
			
			boolean found = false;
			for( int i = 0; i < children.length; i++ )
				if( children[i] instanceof DomainNode && ((DomainNode)children[i]).getDomain().equals("com.example.mbeans"))
					found = true;
			
			assertTrue("Domain \"com.example\" not found", found);
		} finally {
 			projectProvider.dispose();
 			if( launch[0] != null )
 				launch[0].terminate();
		}
	}

	@SuppressWarnings("unchecked")
	protected ILaunchConfigurationWorkingCopy createLaunch() throws Exception {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType launchConfigType = launchManager.getLaunchConfigurationType("org.eclipse.jdt.launching.localJavaApplication");
		ILaunchConfigurationWorkingCopy wc = launchConfigType.newInstance(null, "Test1");

		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "JMX_EXAMPLE");
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "com.example.mbeans.Main");
		wc.setAttribute("org.eclipse.debug.core.MAPPED_RESOURCE_PATHS",
				new ArrayList(Arrays.asList(new String[] {
						"/JMX_EXAMPLE/src/com/example/mbeans/Main.java"
				})));
		wc.setAttribute("org.eclipse.debug.core.MAPPED_RESOURCE_TYPES",
				new ArrayList(Arrays.asList(new String[] {"1"})));
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS,
				"-Dcom.sun.management.jmxremote.port=9999 " +
				"-Dcom.sun.management.jmxremote.authenticate=false " +
				"-Dcom.sun.management.jmxremote.ssl=false");
		return wc;
	}

	private static IExtension[] findExtension(String extensionId) {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IExtensionPoint extensionPoint = registry
				.getExtensionPoint(extensionId);
		return extensionPoint.getExtensions();
	}
}
