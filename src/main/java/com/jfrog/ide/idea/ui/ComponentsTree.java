package com.jfrog.ide.idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.ui.components.JBMenu;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.messages.MessageBusConnection;
import com.jfrog.ide.idea.exclusion.Excludable;
import com.jfrog.ide.idea.exclusion.ExclusionUtils;
import com.jfrog.ide.idea.log.Logger;
import com.jfrog.ide.idea.navigation.NavigationService;
import com.jfrog.ide.idea.navigation.NavigationTarget;
import com.jfrog.ide.idea.ui.menus.ToolbarPopupMenu;
import org.jetbrains.annotations.NotNull;
import org.jfrog.build.extractor.scan.DependencyTree;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author yahavi
 */
public abstract class ComponentsTree extends Tree {
    private static final String SHOW_IN_PROJECT_DESCRIPTOR = "Show in project descriptor";
    private static final String EXCLUDE_DEPENDENCY = "Exclude dependency";

    protected final List<ToolbarPopupMenu> toolbarPopupMenus = new ArrayList<>();
    private final JBPopupMenu popupMenu = new JBPopupMenu();

    protected Project project;

    public ComponentsTree(@NotNull Project project) {
        super((TreeModel) null);
        this.project = project;
        expandRow(0);
        setRootVisible(false);
    }

    protected void reset() {
        setModel(null);
    }

    public void addFilterMenu(ToolbarPopupMenu filterMenu) {
        this.toolbarPopupMenus.add(filterMenu);
    }

    public void addRightClickListener() {
        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleContextMenu(ComponentsTree.this, e);
            }
        };
        addMouseListener(mouseListener);
    }

    private void handleContextMenu(ComponentsTree tree, MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }

        // Event is right-click.
        TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
        if (selPath == null) {
            return;
        }
        createNodePopupMenu((DependencyTree) selPath.getLastPathComponent());
        popupMenu.show(tree, e.getX(), e.getY());
    }

    private void createNodePopupMenu(DependencyTree selectedNode) {
        popupMenu.removeAll();
        NavigationService navigationService = NavigationService.getInstance(project);
        Set<NavigationTarget> navigationCandidates = navigationService.getNavigation(selectedNode);
        DependencyTree affectedNode = selectedNode;
        if (navigationCandidates == null) {
            // Find the direct dependency containing the selected dependency.
            affectedNode = navigationService.getNavigableParent(selectedNode);
            if (affectedNode == null) {
                return;
            }
            navigationCandidates = navigationService.getNavigation(affectedNode);
            if (navigationCandidates == null) {
                return;
            }
        }

        addNodeNavigation(navigationCandidates);
        addNodeExclusion(selectedNode, navigationCandidates, affectedNode);
    }

    private void addNodeNavigation(Set<NavigationTarget> navigationCandidates) {
        if (navigationCandidates.size() > 1) {
            addMultiNavigation(navigationCandidates);
        } else {
            addSingleNavigation(navigationCandidates.iterator().next());
        }
    }

    private void addSingleNavigation(NavigationTarget navigationTarget) {
        popupMenu.add(createNavigationMenuItem(navigationTarget, SHOW_IN_PROJECT_DESCRIPTOR));
    }

    private void addMultiNavigation(Set<NavigationTarget> navigationCandidates) {
        JMenu multiMenu = new JBMenu();
        multiMenu.setText(SHOW_IN_PROJECT_DESCRIPTOR);
        for (NavigationTarget navigationTarget : navigationCandidates) {
            String descriptorPath = getRelativizedDescriptorPath(navigationTarget);
            multiMenu.add(createNavigationMenuItem(navigationTarget, descriptorPath + " " + (navigationTarget.getLineNumber() + 1)));
        }
        popupMenu.add(multiMenu);
    }

    private String getRelativizedDescriptorPath(NavigationTarget navigationTarget) {
        String pathResult = "";
        try {
            VirtualFile descriptorVirtualFile = navigationTarget.getElement().getContainingFile().getVirtualFile();
            pathResult = descriptorVirtualFile.getName();
            String projBasePath = project.getBasePath();
            if (projBasePath == null) {
                return pathResult;
            }
            Path basePath = Paths.get(project.getBasePath());
            Path descriptorPath = Paths.get(descriptorVirtualFile.getPath());
            pathResult = basePath.relativize(descriptorPath).toString();
        } catch (InvalidPathException | PsiInvalidElementAccessException ex) {
            Logger log = Logger.getInstance();
            log.error("Failed getting project-descriptor's path.", ex);
        }
        return pathResult;
    }

    private JMenuItem createNavigationMenuItem(NavigationTarget navigationTarget, String headLine) {
        return new JBMenuItem(new AbstractAction(headLine) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!(navigationTarget.getElement() instanceof Navigatable)) {
                    return;
                }
                Navigatable navigatable = (Navigatable) navigationTarget.getElement();
                if (navigatable.canNavigate()) {
                    navigatable.navigate(true);
                }
            }
        });
    }

    private void addNodeExclusion(DependencyTree nodeToExclude, Set<NavigationTarget> parentCandidates, DependencyTree affectedNode) {
        if (parentCandidates.size() > 1) {
            addMultiExclusion(nodeToExclude, affectedNode, parentCandidates);
        } else {
            addSingleExclusion(nodeToExclude, affectedNode, parentCandidates.iterator().next());
        }
    }

    private void addMultiExclusion(DependencyTree nodeToExclude, DependencyTree affectedNode, Set<NavigationTarget> parentCandidates) {
        if (!ExclusionUtils.isExcludable(nodeToExclude, affectedNode)) {
            return;
        }
        JMenu multiMenu = new JBMenu();
        multiMenu.setText(EXCLUDE_DEPENDENCY);
        for (NavigationTarget parentCandidate : parentCandidates) {
            Excludable excludable = ExclusionUtils.getExcludable(nodeToExclude, affectedNode, parentCandidate);
            if (excludable == null) {
                continue;
            }
            String descriptorPath = getRelativizedDescriptorPath(parentCandidate);
            multiMenu.add(createExcludeMenuItem(excludable, descriptorPath + " " + (parentCandidate.getLineNumber() + 1)));
        }
        if (multiMenu.getItemCount() > 0) {
            popupMenu.add(multiMenu);
        }
    }

    private void addSingleExclusion(DependencyTree nodeToExclude, DependencyTree affectedNode, NavigationTarget parentCandidate) {
        Excludable excludable = ExclusionUtils.getExcludable(nodeToExclude, affectedNode, parentCandidate);
        if (excludable == null) {
            return;
        }
        popupMenu.add(createExcludeMenuItem(excludable, EXCLUDE_DEPENDENCY));
    }

    private JBMenuItem createExcludeMenuItem(Excludable excludable, String headLine) {
        return new JBMenuItem(new AbstractAction(headLine) {
            @Override
            public void actionPerformed(ActionEvent e) {
                excludable.exclude(project);
            }
        });
    }
}
