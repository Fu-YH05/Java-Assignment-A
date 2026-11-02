package com.cst3492.ta.service;

import com.cst3492.ta.history.ChatHistory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Resolves the bound module, validates Java syntax and creates a new file using an undoable IDE command. */
public final class TestFileService {
    private final Project project;

    public TestFileService(Project project) { this.project = project; }

    public PsiJavaFile parse(String code, String expectedPackage) {
        PsiJavaFile file = (PsiJavaFile) PsiFileFactory.getInstance(project)
                .createFileFromText("GeneratedTest.java", JavaFileType.INSTANCE, code);
        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement.class) != null || file.getClasses().length != 1
                || file.getClasses()[0].isInterface() || file.getClasses()[0].isEnum() || file.getClasses()[0].isRecord()
                || file.getClasses()[0].getName() == null) {
            throw new IllegalArgumentException("The response must contain one complete, syntactically valid Java test class. Refine the scenario and generate again.");
        }
        if (!file.getPackageName().equals(expectedPackage))
            throw new IllegalArgumentException("The generated package does not match the bound source package (" + expectedPackage + "). Generate again with the correct package.");
        return file;
    }

    public String relativePath(String code, String expectedPackage) {
        PsiJavaFile file = parse(code, expectedPackage);
        return (file.getPackageName().isBlank() ? "" : file.getPackageName().replace('.', '/') + "/")
                + file.getClasses()[0].getName() + ".java";
    }

    public String previewPath(ChatHistory.Session conversation, String code) {
        String relative = relativePath(code, conversation.sourcePackage);
        VirtualFile source = LocalFileSystem.getInstance().findFileByPath(conversation.sourcePath);
        Module module = source == null ? null : ModuleUtilCore.findModuleForFile(source, project);
        if (module == null) return "Select a valid Java module / " + relative;
        List<VirtualFile> roots = ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE);
        if (roots.size() == 1) return roots.getFirst().getPath() + "/" + relative;
        if (roots.size() > 1) return "Choose test source root / " + relative;
        VirtualFile content = ProjectFileIndex.getInstance(project).getContentRootForFile(source);
        return (content == null ? "Test source root" : content.getPath() + "/src/test/java") + "/" + relative;
    }

    private record Destination(Module module, List<VirtualFile> roots, VirtualFile contentRoot,
                               String relative, boolean hasJunit, String error) { }

    private Destination resolve(ChatHistory.Session conversation, ChatHistory.Turn turn) {
        try {
            String relative = relativePath(turn.testCode, conversation.sourcePackage);
            VirtualFile source = LocalFileSystem.getInstance().findFileByPath(conversation.sourcePath);
            if (source == null) throw new IOException("The bound source file was moved or deleted. Start a new test task from its current location.");
            Module module = ModuleUtilCore.findModuleForFile(source, project);
            if (module == null) throw new IOException("The bound file is not part of a Java module.");
            return new Destination(module, ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE),
                    ProjectFileIndex.getInstance(project).getContentRootForFile(source), relative,
                    JavaPsiFacade.getInstance(project).findClass("org.junit.jupiter.api.Test",
                            GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)) != null, null);
        } catch (com.intellij.openapi.progress.ProcessCanceledException canceled) { throw canceled; }
        catch (Exception error) { return new Destination(null, List.of(), null, "", false, error.getMessage() == null ? error.toString() : error.getMessage()); }
    }

    public void createOrOpen(ChatHistory.Session conversation, ChatHistory.Turn turn, Runnable finished) {
        if (!turn.filePath.isBlank()) {
            try {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(turn.filePath);
                if (file == null || !file.isValid()) throw new IOException("The created file was moved or deleted: " + turn.filePath);
                FileEditorManager.getInstance(project).openFile(file, true);
            } catch (Exception error) { showError(error); }
            finally { finished.run(); }
            return;
        }
        ReadAction.nonBlocking(() -> resolve(conversation, turn)).inSmartMode(project).expireWith(project)
                .finishOnUiThread(ModalityState.defaultModalityState(), destination -> {
                    try { create(turn, destination); }
                    catch (Exception error) { showError(error); }
                    finally { finished.run(); }
                }).submit(AppExecutorUtil.getAppExecutorService());
    }

    private void create(ChatHistory.Turn turn, Destination destination) throws IOException {
        if (destination.error != null) throw new IOException(destination.error);
        Module module = destination.module;
        if (module.isDisposed()) throw new IOException("The bound module was closed. Start a new test task.");
        List<VirtualFile> roots = destination.roots;
        String relative = destination.relative;
        VirtualFile root;
        VirtualFile contentRoot = destination.contentRoot;
        boolean registerRoot = roots.isEmpty();
        if (registerRoot) {
            if (contentRoot == null) throw new IOException("No content root was found for the bound source file.");
            root = contentRoot;
        } else if (roots.size() == 1) root = roots.getFirst();
        else {
            String[] paths = roots.stream().map(VirtualFile::getPath).toArray(String[]::new);
            int choice = Messages.showChooseDialog(project, "Select the test source root for this module:",
                    "Create Test File", Messages.getQuestionIcon(), paths, paths[0]);
            if (choice < 0) return;
            root = roots.get(choice);
        }
        if (!destination.hasJunit && Messages.showYesNoDialog(project, "JUnit 5 was not found in this module. Create the file anyway? Add the dependency before running it.",
                "JUnit 5 Dependency", Messages.getWarningIcon()) != Messages.YES) return;
        VirtualFile targetRoot = root;
        java.util.concurrent.atomic.AtomicReference<VirtualFile> created = new java.util.concurrent.atomic.AtomicReference<>();
        WriteCommandAction.runWriteCommandAction(project, "Create generated JUnit test", null, () -> {
            try {
                VirtualFile testRoot = registerRoot ? VfsUtil.createDirectoryIfMissing(targetRoot, "src/test/java") : targetRoot;
                if (testRoot == null) throw new IOException("Cannot create the test source root.");
                if (testRoot.findFileByRelativePath(relative) != null)
                    throw new IOException("A file already exists at " + testRoot.getPath() + "/" + relative + ". It has not been overwritten. Rename or move it before creating this result.");
                int slash = relative.lastIndexOf('/');
                VirtualFile directory = slash < 0 ? testRoot : VfsUtil.createDirectoryIfMissing(testRoot, relative.substring(0, slash));
                if (directory == null) throw new IOException("Cannot create the test package directory.");
                VirtualFile file = directory.createChildData(this, relative.substring(slash + 1));
                file.setBinaryContent(turn.testCode.getBytes(StandardCharsets.UTF_8));
                turn.filePath = file.getPath();
                if (registerRoot) {
                    var model = ModuleRootManager.getInstance(module).getModifiableModel();
                    try {
                        for (var entry : model.getContentEntries()) {
                            if (targetRoot.equals(entry.getFile())) { entry.addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE); break; }
                        }
                        model.commit();
                    } finally { if (!model.isDisposed()) model.dispose(); }
                }
                created.set(file);
            } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
        });
        FileEditorManager.getInstance(project).openFile(created.get(), true);
    }

    private void showError(Exception error) {
        Messages.showErrorDialog(project, error.getMessage() == null ? error.toString() : error.getMessage(), "Cannot Create Test File");
    }
}