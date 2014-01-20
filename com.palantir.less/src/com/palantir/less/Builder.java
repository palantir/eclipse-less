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
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * The Less builder.
 *
 * @author aramaswamy
 */
public final class Builder extends IncrementalProjectBuilder {

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
        try {
            String lessFile = Builder.getRootLessFile(this.getProject());
            compile(lessFile);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getRootLessFile(IProject project) throws CoreException {
        // stop-gap until configurable src/dst is added

        final ImmutableList.Builder<String> files = ImmutableList.builder();
        project.accept(new IResourceVisitor() {
            @Override
            public boolean visit(IResource resource) throws CoreException {
                if (isLessFile(resource) && resource.getName().equals("app.less")) {
                    files.add(resource.getRawLocation().toOSString());
                }
                return true;
            }
        });
        return files.build().get(0);
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

    private static void compile(String fileName) throws CoreException {
        File nodeFile = findNode();
        String nodePath = nodeFile.getAbsolutePath();
        String outputFileName = fileName.replace(".less", ".css");

        // get the path to the main.js file
        File bundleFile;
        try {
            bundleFile = FileLocator.getBundleFile(UIPlugin.getDefault().getBundle());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File mainFile = new File(bundleFile, "bin/main.js");
        String mainPath = mainFile.getAbsolutePath();

        // create the command
        List<String> command = Lists.newArrayList();
        command.add(nodePath);
        command.add(mainPath);
        command.add(fileName);
        command.add(outputFileName);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            process.waitFor();
            Path path = new Path(outputFileName);
            IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(path);

            // refresh the resource for the file if it is within the workspace
            if (file != null) {
                file.refreshLocal(IResource.DEPTH_ZERO, null);
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
