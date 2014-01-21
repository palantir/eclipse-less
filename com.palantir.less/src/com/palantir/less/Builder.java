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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * The Less builder.
 *
 * @author aramaswamy
 */
public final class Builder extends IncrementalProjectBuilder {

    private static final String PREFERENCES_OUTPUT_FILE_NAME = "dest";

    private static final String PREFERENCES_SOURCE_FILE_NAME = "src";

    public static final String ID = "com.palantir.less.lessBuilder";

    private static final String OS_NAME = System.getProperty("os.name");
    private static final Splitter PATH_SPLITTER = Splitter.on(File.pathSeparatorChar);

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        boolean lessNeedsRebuild;

        switch (kind) {
            case IncrementalProjectBuilder.AUTO_BUILD:
            case IncrementalProjectBuilder.INCREMENTAL_BUILD:
                ImmutableList<String> filesToRebuild = this.changedLessFiles();
                lessNeedsRebuild = (!filesToRebuild.isEmpty());
                break;

            case IncrementalProjectBuilder.FULL_BUILD:
                lessNeedsRebuild = true;
                break;

            default:
                throw new IllegalArgumentException();
        }

        if (lessNeedsRebuild) {
            this.fullBuild();
        }

        return null;
    }

    private void fullBuild() {
        IProject project = this.getProject();

        IScopeContext projectScope = new ProjectScope(project);
        IEclipsePreferences prefs = projectScope.getNode(Builder.ID);

        String relativeSourcePath = prefs.get(PREFERENCES_SOURCE_FILE_NAME, null);
        String relativeDestinationPath = prefs.get(PREFERENCES_OUTPUT_FILE_NAME, null);

        if (relativeSourcePath == null) {
            throw new RuntimeException("the LESS builder does not have a source file; please set one");
        } else if (relativeDestinationPath == null) {
            throw new RuntimeException("the LESS builder does not have an output file; please set one");
        }

        IFile source = project.getFile(relativeSourcePath);
        IFile destination = project.getFile(relativeDestinationPath);

        try {
            compile(source, destination);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    private ImmutableList<String> changedLessFiles() throws CoreException {
        final ImmutableList.Builder<String> files = ImmutableList.builder();
        IResourceDelta delta = this.getDelta(this.getProject());

        delta.accept(new IResourceDeltaVisitor(){
            @Override
            public boolean visit(IResourceDelta delta) throws CoreException {
                if (isLessFile(delta.getResource())) {
                    IFile file = (IFile) delta.getResource();
                    String path = file.getProjectRelativePath().toOSString();
                    files.add(path);
                }
                return true;
            }
        });

        return files.build();
    }

    private static void compile(IFile input, IFile output) throws CoreException {
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
            .add(input.getLocation().toOSString())
            .add(output.getLocation().toOSString())
            .build();

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            process.waitFor();
            // refresh the resource for the file if it is within the workspace
            if (output.exists()) {
                output.refreshLocal(IResource.DEPTH_ZERO, null);
            }
        } catch (InterruptedException e) {
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
        if (resource == null || resource.getType() != IResource.FILE) {
            return false;
        }
        String ext = resource.getFileExtension();
        return (ext != null && ext.equals("less"));
    }
}
