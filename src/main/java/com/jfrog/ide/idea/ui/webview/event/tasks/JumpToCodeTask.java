package com.jfrog.ide.idea.ui.webview.event.tasks;

import com.intellij.openapi.project.Project;
import com.jfrog.ide.idea.inspections.JumpToCode;
import com.jfrog.ide.idea.ui.webview.model.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a task that performs the "Jump to Code" action in the IDE.
 * This task is responsible for executing the jump to code operation based on the provided project and location.
 */
public class JumpToCodeTask {
    JumpToCode jumpToCode;

    public JumpToCodeTask(@NotNull Project project) {
        jumpToCode = new JumpToCode(project);
    }

    public void execute(Location location) {
        this.jumpToCode.execute(location.getFile(), location.getStartRow(), location.getEndRow(), location.getStartColumn(), location.getEndColumn());
    }
}