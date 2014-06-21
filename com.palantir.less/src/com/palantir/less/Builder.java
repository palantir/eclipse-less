/*
 * Copyright 2013 Palantir Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.less;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * The Less builder.
 *
 * @author aramaswamy
 */
public final class Builder extends IncrementalProjectBuilder {

    public static final String ID = "com.palantir.less.lessBuilder";

    private static final String OS_NAME = StandardSystemProperty.OS_NAME.value();
    private static final Splitter PATH_SPLITTER = Splitter.on(File.pathSeparatorChar);

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        boolean build = true;

        switch (kind) {
            case IncrementalProjectBuilder.AUTO_BUILD:
            case IncrementalProjectBuilder.INCREMENTAL_BUILD:
                build = this.hasLessFileChanged();
                break;
        }

        if (build) {
            this.compileLessFiles(monitor);
        }

        return null;
    }

    private void compileLessFiles(IProgressMonitor monitor) {
        IProject project = this.getProject();
        IScopeContext projectScope = new ProjectScope(project);
        IEclipsePreferences prefs = projectScope.getNode(UIPlugin.ID);
        String srcFiles = prefs.get("srcFiles", null);
        String outDir = prefs.get("outDir", null);

        if (srcFiles != null) {
            for (String srcFile : Splitter.on(';').split(srcFiles)) {
                IFile lessFile = project.getFile(srcFile);
                String cssFileName = srcFile.replace(".less", ".css");

                if (outDir != null) {
                    List<String> splitList = Splitter.on('/').splitToList(cssFileName);

                    cssFileName = outDir + '/' + splitList.get(splitList.size() - 1);
                }

                IFile cssFile = project.getFile(cssFileName);

                compileLessFile(lessFile, cssFile, monitor);
            }
        }
    }

    private boolean hasLessFileChanged() throws CoreException {
        final AtomicBoolean lessFileChanged = new AtomicBoolean();
        IResourceDelta delta = this.getDelta(this.getProject());

        delta.accept(new IResourceDeltaVisitor() {
            @Override
            public boolean visit(IResourceDelta delta) throws CoreException {
                IResource resource = delta.getResource();

                if (isLessFile(resource)) {
                    lessFileChanged.set(true);

                    // no need to continue visiting resources in this delta
                    return false;
                }

                return true;
            }
        });

        return lessFileChanged.get();
    }

    private static void compileLessFile(IFile lessFile, IFile cssFile, IProgressMonitor monitor) {
        File nodeFile = findNode();

        // get the path to the main.js file
        File bundleFile;
        try {
            bundleFile = FileLocator.getBundleFile(UIPlugin.getDefault().getBundle());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File mainFile = new File(bundleFile, "bin/main.js");

        // create the command
        ImmutableList<String> command = new ImmutableList.Builder<String>()
                .add(nodeFile.getAbsolutePath())
                .add(mainFile.getAbsolutePath())
                .add(lessFile.getLocation().toOSString())
                .add(cssFile.getLocation().toOSString())
                .build();

        // compile the less file
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // wait for the compilation to complete
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // refresh the CSS file to ensure it is up-to-date within Eclipse
        try {
            cssFile.refreshLocal(IResource.DEPTH_ZERO, monitor);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static File findNode() {
        String nodeFileName = getNodeFileName();
        String path = System.getenv("PATH");
        List<String> directories = Lists.newArrayList(PATH_SPLITTER.split(path));

        // ensure /usr/local/bin is included for OS X
        if (OS_NAME.startsWith("Mac OS X")) {
            directories.add("/usr/local/bin");
        }

        // search for Node.js in the PATH directories
        for (String directory : directories) {
            File nodeFile = new File(directory, nodeFileName);

            if (nodeFile.exists()) {
                return nodeFile;
            }
        }

        throw new IllegalStateException("Could not find Node.js.");
    }

    private static String getNodeFileName() {
        if (OS_NAME.startsWith("Windows")) {
            return "node.exe";
        }

        return "node";
    }

    private static boolean isLessFile(IResource resource) {
        return resource.getType() == IResource.FILE && resource.getName().endsWith(".less");
    }
}
