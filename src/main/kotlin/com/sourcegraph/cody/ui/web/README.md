# Native Webviews in Cody for JetBrains

This package implements native Agent Webviews for JetBrains IDEs. A "native" webview is one that runs web content&mdash;HTML, JavaScript and CSS. The Cody TypeScript extension calls the VSCode Webview API. Agent's VSCode shim will proxy this API into Agent Protocol messages, when Agent is configured with these client capabilities:

```json
{
  "webview": "native",
  "webviewNativeConfig": {
    "view": "multiple",
    "cspSource": "'self' https://*.sourcegraphstatic.com",
    "webviewBundleServingPrefix": "https://file+.sourcegraphstatic.com"
  },
  "webviewMessages": "String-encoded",
  ...
}
```

This package sinks the Webview-specific parts of the Agent protocol (see [WebUIAgentBinding.kt](WebUIAgentBinding.kt)) and forwards them to [WebUIService](WebUIService.kt) which provides the implementation of the API.

The implementation is layered:

- Public layers:
  - [WebUIAgentBinding.kt](WebUIAgentBinding.kt) binds the Webview Agent protocol to WebUIService.
  - [WebUIService](WebUIService.kt) provides Webviews to the plugin, and to Agent via WebUIAgentBinding.
- Internal package implementation layers:
  - [WebviewView.kt](WebviewView.kt) (and related) implements the Webview view API by hosting WebUIProxy instances in Cody's Tool Window (although this is easy to generalize to other Tool Windows.)
  - [WebviewPanel.kt](WebviewPanel.kt) (and related) implements the Webview panel API by hosting WebUIProxy instances in FileEditors. 
  - [WebUIHost](WebUIHost.kt) abstracts some details of the host&mdash;which may be a ToolWindow or an Editor, and uses Agent&mdash;from the WebUIProxy.
  - [WebUIProxy](WebUIProxy.kt) the lowest level: Wraps a browser, handles resource requests and postMessage, etc. We use JBCEF, but this is an implementation detail contained at this layer.