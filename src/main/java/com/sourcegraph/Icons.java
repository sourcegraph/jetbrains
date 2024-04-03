package com.sourcegraph;

import com.intellij.openapi.util.IconLoader;
import javax.swing.*;

public interface Icons {
  Icon SourcegraphLogo = IconLoader.getIcon("/icons/sourcegraphLogo.svg", Icons.class);
  Icon CodyLogo = IconLoader.getIcon("/icons/codyLogo.svg", Icons.class);
  Icon GearPlain = IconLoader.getIcon("/icons/gearPlain.svg", Icons.class);
  // TODO: When design provides SVG for generic code hosts, fix this icon.
  Icon RepoHostGeneric = null;
  // TODO: When design provides SVG for GitHub, fix this icon.
  Icon RepoHostGitHub = null;
  Icon RepoHostGitLab = IconLoader.getIcon("/icons/repo-host-gitlab.svg", Icons.class);
}
