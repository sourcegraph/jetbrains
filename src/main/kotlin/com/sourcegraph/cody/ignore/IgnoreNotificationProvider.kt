package com.sourcegraph.cody.ignore

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

// TODO: Update notifications when the policy changes
//       EditorNotifications.getInstance(project).updateAllNotifications()

class IgnoreNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {

    return Function {
      EditorNotificationPanel(it).apply {
        // TODO: This message is specific to the enterprise product and needs to be changed when we support cody ignore in the self-serve product
        text = "Cody: Admin Ignore settings have excluded this repo's content from all Cody features and context"

        createActionLabel(
          "Learn about ignored files",
          Runnable {
            // TODO: Documentation at this link describes the experimental self-serve feature, ensure this is relevant for enterprises
            BrowserUtil.browse("https://sourcegraph.com/docs/cody/capabilities/ignore-context")
          },
          false
        )
      }
    }
  }
}