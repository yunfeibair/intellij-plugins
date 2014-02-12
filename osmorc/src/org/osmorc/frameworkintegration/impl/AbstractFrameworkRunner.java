/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osmorc.frameworkintegration.impl;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.osmorc.frameworkintegration.*;
import org.osmorc.util.OsgiFileUtil;
import org.osmorc.run.OsgiRunConfiguration;
import org.osmorc.run.ui.SelectedBundle;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.osmorc.frameworkintegration.FrameworkInstanceManager.FrameworkBundleType;

/**
 * This class provides a default implementation for a part of the FrameworkRunner interface.
 *
 * @author Robert F. Beeger (robert@beeger.net)
 * @author <a href="mailto:janthomae@janthomae.de">Jan Thom&auml;</a>
 */
public abstract class AbstractFrameworkRunner implements FrameworkRunner {
  protected OsgiRunConfiguration myRunConfiguration;
  protected FrameworkInstanceDefinition myInstance;
  protected FrameworkIntegrator myIntegrator;
  protected FrameworkInstanceManager myInstanceManager;
  protected Map<String, String> myAdditionalProperties;
  protected List<SelectedBundle> myBundles;

  private File myWorkingDir;

  @Override
  public JavaParameters createJavaParameters(@NotNull OsgiRunConfiguration runConfiguration,
                                             @NotNull List<SelectedBundle> bundles) throws ExecutionException {
    myRunConfiguration = runConfiguration;
    myInstance = myRunConfiguration.getInstanceToUse();
    assert myInstance != null : myRunConfiguration;
    myIntegrator = FrameworkIntegratorRegistry.getInstance().findIntegratorByInstanceDefinition(myInstance);
    assert myIntegrator != null : myInstance;
    myInstanceManager = myIntegrator.getFrameworkInstanceManager();
    myAdditionalProperties = myRunConfiguration.getAdditionalProperties();
    myBundles = bundles;

    // validation

    Sdk jdkForRun;
    if (myRunConfiguration.isUseAlternativeJre()) {
      String path = myRunConfiguration.getAlternativeJrePath();
      if (StringUtil.isEmpty(path) || !JdkUtil.checkForJre(path)) {
        jdkForRun = null;
      }
      else {
        jdkForRun = JavaSdk.getInstance().createJdk("", myRunConfiguration.getAlternativeJrePath());
      }
    }
    else {
      jdkForRun = ProjectRootManager.getInstance(myRunConfiguration.getProject()).getProjectSdk();
    }
    if (jdkForRun == null) {
      throw CantRunException.noJdkConfigured();
    }

    JavaParameters params = new JavaParameters();

    // working directory and JVM

    if (myRunConfiguration.isGenerateWorkingDir()) {
      myWorkingDir = new File(PathManager.getSystemPath(), "osmorc/run." + System.currentTimeMillis());
    }
    else {
      myWorkingDir = new File(myRunConfiguration.getWorkingDir());
    }
    if (!myWorkingDir.isDirectory() && !myWorkingDir.mkdirs()) {
      throw new CantRunException("Cannot create work directory '" + myWorkingDir.getPath() + "'");
    }
    params.setWorkingDirectory(myWorkingDir);

    // only add JDK classes to the classpath, the rest is to be provided by bundles
    params.configureByProject(myRunConfiguration.getProject(), JavaParameters.JDK_ONLY, jdkForRun);

    // class path

    Collection<SelectedBundle> systemBundles = myInstanceManager.getFrameworkBundles(myInstance, FrameworkBundleType.SYSTEM);
    if (systemBundles.isEmpty()) {
      throw new CantRunException("Libraries required to start the framework not found - please check the installation");
    }
    for (SelectedBundle bundle : systemBundles) {
      String url = bundle.getBundleUrl();
      assert url != null : bundle;
      params.getClassPath().add(OsgiFileUtil.urlToPath(url));
    }

    if (GenericRunProperties.isStartConsole(myAdditionalProperties)) {
      Collection<SelectedBundle> shellBundles = myInstanceManager.getFrameworkBundles(myInstance, FrameworkBundleType.SHELL);
      if (shellBundles.isEmpty()) {
        throw new CantRunException("Console requested but no shell bundles can be found - please check the installation");
      }
      List<SelectedBundle> allBundles = ContainerUtil.newArrayList(shellBundles);
      allBundles.addAll(myBundles);
      myBundles = allBundles;
    }

    if (myRunConfiguration.isIncludeAllBundlesInClassPath()) {
      for (SelectedBundle bundle : myBundles) {
        String url = bundle.getBundleUrl();
        if (url != null) {
          params.getClassPath().add(OsgiFileUtil.urlToPath(url));
        }
      }
    }

    // runner options

    params.setUseDynamicVMOptions(!myBundles.isEmpty());

    params.getVMParametersList().addAll(HttpConfigurable.convertArguments(HttpConfigurable.getJvmPropertiesList(false, null)));
    params.getVMParametersList().addParametersString(myRunConfiguration.getVmParameters());

    String additionalProgramParams = myRunConfiguration.getProgramParameters();
    if (!StringUtil.isEmptyOrSpaces(additionalProgramParams)) {
      params.getProgramParametersList().addParametersString(additionalProgramParams);
    }

    String bootDelegation = GenericRunProperties.getBootDelegation(myAdditionalProperties);
    if (!StringUtil.isEmptyOrSpaces(bootDelegation)) {
      params.getVMParametersList().addProperty("org.osgi.framework.bootdelegation", bootDelegation);
    }

    String systemPackages = GenericRunProperties.getSystemPackages(myAdditionalProperties);
    if (!StringUtil.isEmptyOrSpaces(systemPackages)) {
      params.getVMParametersList().addProperty("org.osgi.framework.system.packages.extra", systemPackages);
    }

    // framework-specific options

    setupParameters(params);

    return params;
  }

  protected abstract void setupParameters(@NotNull JavaParameters parameters);

  protected int getBundleStartLevel(@NotNull SelectedBundle bundle) {
    return bundle.isDefaultStartLevel() ? myRunConfiguration.getDefaultStartLevel() : bundle.getStartLevel();
  }

  protected int getFrameworkStartLevel() {
    if (myRunConfiguration.isAutoStartLevel()) {
      int startLevel = 0;
      for (SelectedBundle bundle : myBundles) {
        int bundleStartLevel = getBundleStartLevel(bundle);
        startLevel = Math.max(bundleStartLevel, startLevel);
      }
      return startLevel;
    }
    else {
      return myRunConfiguration.getFrameworkStartLevel();
    }
  }

  @Override
  public void dispose() {
    if (myRunConfiguration.isGenerateWorkingDir() && myWorkingDir != null) {
      FileUtil.asyncDelete(myWorkingDir);
    }
  }
}
