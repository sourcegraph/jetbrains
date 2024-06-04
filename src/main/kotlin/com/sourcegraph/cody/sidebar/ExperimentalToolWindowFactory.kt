package com.sourcegraph.cody.sidebar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.handler.CefFocusHandler
import org.cef.handler.CefFocusHandlerAdapter
import org.cef.handler.CefLifeSpanHandler
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField

class ExperimentalToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    doStuff(project, toolWindow)
  }

  fun doStuff(project: Project, toolWindow: ToolWindow) {
    val textField = JTextField()
    val button = JButton().apply {
      text = "Delayed focus to north text field"
      addActionListener {
        ApplicationManager.getApplication().executeOnPooledThread {
          Thread.sleep(1000)
          runInEdt { textField.requestFocus() }
        }
      }
    }

    val browserClient = JBCefApp.getInstance().createClient()
    // false: animation has vsync, weird resizing
    // true: animation does not have vsync, better resizing
    val offscreenRendering = false
    val embeddedBrowser = JBCefBrowserBuilder().setClient(browserClient).setOffScreenRendering(offscreenRendering).build()
    browserClient.addFocusHandler(object : CefFocusHandlerAdapter() {
      override fun onGotFocus(browser: CefBrowser) {
        println("Got focus")
        super.onGotFocus(browser)
      }

      override fun onSetFocus(browser: CefBrowser?, source: CefFocusHandler.FocusSource?): Boolean {
        println("set focus")
        return super.onSetFocus(browser, source)
      }

      override fun onTakeFocus(browser: CefBrowser?, next: Boolean) {
        println("take focus")
        // TODO: This is cheesy just to demonstrate we can fix the tab out problem.
        if (next) { button } else { textField }.requestFocusInWindow()
      }
    }, embeddedBrowser.cefBrowser)

    val findButton = JButton().apply {
      text = "Find"
      addActionListener {
        embeddedBrowser.cefBrowser.stopFinding(true)
        embeddedBrowser.cefBrowser.find(42, textField.text, true, false, true)
      }
    }

    val browserComponent = embeddedBrowser.component

    val wrapper = object : JPanel() {
      override fun doLayout() {
        super.doLayout()
        val r = bounds
        browserComponent.bounds = Rectangle(0, 0, r.width, r.height)
        if (offscreenRendering) {
          findButton.bounds = Rectangle(r.width - 220, r.height - 60, 200, 40)
        }
      }
    }
    if (offscreenRendering) {
      wrapper.add(findButton)
    }
    wrapper.add(browserComponent)

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener() { event ->
      println("KFM ${event.propertyName}: ${event.newValue} <= ${event.oldValue}")
    }

    val panel = JPanel().apply {
      layout = BorderLayout()
    }
    panel.add(textField, BorderLayout.NORTH)
    panel.add(if (offscreenRendering) { wrapper } else { browserComponent }, BorderLayout.CENTER)
    panel.add(button, BorderLayout.SOUTH)
    if (!offscreenRendering) {
      panel.add(findButton, BorderLayout.EAST)
    }
    ContentFactory.SERVICE.getInstance().createContent(panel, "TODO Content Title", false).apply {
      isCloseable = false
      isPinnable = true
      isPinned = true
      toolWindow.contentManager.addContent(this)
    }

    embeddedBrowser.loadHTML("""<!DOCTYPE html>
      |<style>
#circle {
  width: 2em;
  height: 2em;
  border-radius: 1em;
  background: red;
  position: absolute;
  left: calc(50% - 1em);
  transition: 1s background;
}

#container:hover #circle {
  background: green;
}

#container:hover {
  rotate: 180deg;
}

#container {
  transition: 1s rotate;
  rotate: 0deg;
  width: 10em;
  height: 10em;
  animation-name: spin;
  animation-duration: 2s;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

@keyframes spin {
  from {
    rotate: 0;
  }
  to {
    rotate: 360deg;
  }
}
      |</style>
      |<script>window.n = 0</script>
      |<input type="text" autofocus>
      |<p>
      |<label for="box2">Box2:</label>
      |<input type="text" id="box2">
      |<p>
      |<textarea>
      |</textarea>
      |<button onclick="javascript:setTimeout(function () { document.querySelector('#box2').focus() }, 1000)">1s delayed focus to box2</button>
      |<button onclick="window.history.go(0)">Reload</button>
      |<button onclick="event.target.textContent = window.n++">stuff</button>
      |<p>
      |<a href="https://google.com/">Navigate to a website</a>
      |<p>
      |<div id="container">
      |<div id="circle">&nbsp;</div>
      |</div>
      |<p>
      |<input type="checkbox" id="stopPropagation"><label for="cancel">Stop Progagation</label><br>
<input type="checkbox" id="preventDefault"><label for="cancel">Prevent Default</label><br>
<input type="text" id="hotzone">
<p>
  <textarea rows="20" cols="40" id="log"></textarea>
</p>
<script>
const logArea = document.querySelector('#log')
const preventDefault = document.querySelector('#preventDefault')
const stopPropagation = document.querySelector('#stopPropagation')


function log(message) {
  logArea.textContent = message + '\n' + logArea.textContent
}

let hotzone = document.querySelector('#hotzone')
hotzone.addEventListener('keyup', (e) => {
  const modifiers = ['Alt', 'AltGraph', 'CapsLock', 'Control', 'Meta', 'NumLock', 'OS', 'ScrollLock']
  const activeModifiers = []
  for (const modifier of modifiers) {
    if (e.getModifierState(modifier)) {
      activeModifiers.push(modifier)
    }
  }
  log(`${'$'}{e.key} (${'$'}{e.code}) modifiers: ${'$'}{activeModifiers} composing? ${'$'}{e.isComposing}`)
  if (preventDefault.checked) {
    e.preventDefault()
  }
  if (stopPropagation.checked) {
    e.stopPropagation()
  }
})
</script>
      |""".trimMargin())
  }
}
