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
package org.jboss.tools.jmx.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * This class is in charge of loading all extensions
 * declared in this application, as well as providing
 * getters so others can gain access to them.
 *
 */
public class ExtensionManager {
	public static final String MBEAN_CONNECTION = "org.jboss.tools.jmx.core.MBeanServerConnectionProvider"; //$NON-NLS-1$
	public static final String CLASS = "class"; //$NON-NLS-1$
	private static HashMap<String, IConnectionProvider> providers;

	public static IConnectionProvider[] getProviders() {
		if (providers == null)
			loadExtensions();
		// Sort
		ArrayList<IConnectionProvider> ret = new ArrayList<IConnectionProvider>();
		ret.addAll(providers.values());
		Collections.sort(ret, new Comparator<IConnectionProvider>() {
			public int compare(IConnectionProvider arg0,
					IConnectionProvider arg1) {
				return arg0.getId().compareTo(arg1.getId());
			} });
		return ret.toArray(new IConnectionProvider[ret.size()]);
	}

	public static IConnectionProvider getProvider(String id) {
		if (providers == null)
			loadExtensions();
		return providers.get(id);
	}

	public static IConnectionWrapper[] getAllConnections() {
		if (providers == null)
			loadExtensions();
		ArrayList<IConnectionWrapper> l = new ArrayList<IConnectionWrapper>();
		for( Iterator<IConnectionProvider> i = providers.values().iterator(); i.hasNext();) {
			l.addAll(Arrays.asList(i.next().getConnections()));
		}
		return l.toArray(new IConnectionWrapper[l.size()]);
	}

	public static void loadExtensions() {
		HashMap<String, IConnectionProvider> tmp = new HashMap<String, IConnectionProvider>();
		IExtension[] extensions = findExtension(MBEAN_CONNECTION);
		for (int i = 0; i < extensions.length; i++) {
			IConfigurationElement elements[] = extensions[i]
					.getConfigurationElements();
			for (int j = 0; j < elements.length; j++) {
				try {
					IConnectionProvider o = (IConnectionProvider) elements[j]
							.createExecutableExtension(CLASS);
					tmp.put(o.getId(),o);
				} catch (InvalidRegistryObjectException e) {
					IStatus s = new Status(IStatus.ERROR, JMXActivator.PLUGIN_ID, JMXCoreMessages.ExtensionManagerError1,e);
					JMXActivator.log(s);
				} catch (CoreException e) {
					IStatus s = new Status(IStatus.ERROR, JMXActivator.PLUGIN_ID, JMXCoreMessages.ExtensionManagerError1,e);
					JMXActivator.log(s);
				}
			}
		}
		providers = tmp;
	}

	private static IExtension[] findExtension(String extensionId) {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IExtensionPoint extensionPoint = registry
				.getExtensionPoint(extensionId);
		return extensionPoint.getExtensions();
	}

	public static void addConnectionProviderListener(IConnectionProviderListener listener) {
		IConnectionProvider[] providers2 = getProviders();
		for( int i = 0; i < providers2.length; i++)
			providers2[i].addListener(listener);
	}
	public static void removeConnectionProviderListener(IConnectionProviderListener listener) {
		IConnectionProvider[] providers2 = getProviders();
		for( int i = 0; i < providers2.length; i++)
			providers2[i].removeListener(listener);
	}
}
