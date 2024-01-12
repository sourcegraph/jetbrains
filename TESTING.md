## Checklist

- Onboarding
    - [ ] [Sign in with GitHub](#sign-in-with-github)
    - [ ] [Sign in with GitLab](#sign-in-with-gitlab)
    - [ ] [Sign in with Google](#sign-in-with-google)
- Autocomplete
    - [ ] [Single-line autocomplete](#single-line-autocomplete)
    - [ ] [Multi-line autocomplete](#multi-line-autocomplete)
    - [ ] [Infilling autocomplete](#infilling-autocomplete)
    - [ ] [Cycle through autocomplete](#cycle-through-autocomplete)
- Commands
    - [ ] [General commands availability in toolbar](#general-commands-availability-in-cody-toolbar)
    - [ ] [General commands availability in context menu](#general-commands-availability-in-context-menu)
    - [ ] [Explain Selected Code (Detailed)](#explain-selected-code-detailed)
    - [ ] [Explain Selected Code (High Level)](#explain-selected-code-high-level)
    - [ ] [Generate Docstring](#generate-docstring)
    - [ ] [Generate Unit Test](#generate-unit-test)
    - [ ] [Improve Variable Names](#improve-variable-names)
    - [ ] [Smell Code](#smell-code)
- Chat
    - [ ] [Autoscroll to latest message](#autoscroll-to-latest-message)
    - [ ] [Read chat history without interruptions](#read-chat-history-without-interruptions)
- Product-led growth
    - [ ] [Cody Free/Pro subscription tab](#cody-freepro-subscription-tab)
    - [ ] [Cody Free/Pro rate limit errors](#cody-freepro-rate-limit-errors)
- Other
    - [ ] [Search Selection on Sourcegraph Web](#search-selection-on-sourcegraph-web)
    - [ ] [Automatic repository recognition](#automatic-repository-recognition)
    - [ ] [Persistent custom repository](#persistent-custom-repository)
    - [ ] [Colour scheme changes](#colour-scheme-changes)

## Onboarding

### Sign in with GitHub

Prerequisite: You have to **sign out** from all existing accounts.

1. Navigate to `Cody` toolbar and use `Sign in with GitHub`.
2. Browser is launched automatically and IDE freezes with spinning `Login to Sourcegraph` dialog.
3. Authorize with a valid account.

#### Expected behaviour

* IDE should receive a valid token automatically.
* `Commands` and `Chat` tabs are ready to use.

### Sign in with GitLab

Onboarding through GitLab is similar to [Sign in with GitHub](#sign-in-with-github), and the authorization is also done
through the browser. Expected behaviour is identical.

### Sign in with Google

Onboarding through GitLab is similar to [Sign in with GitHub](#sign-in-with-github), and the authorization is also done
through the browser. Expected behaviour is identical.

## Autocomplete

### Single-line autocomplete

1. Paste the following Java code:
    ```java
    // print Hello World!
    System.out.
    ```
2. Place a cursor at the end of the `System.out.` line.
3. Trigger autocompletion with <kbd>Alt</kbd> + <kbd>/</kbd>.

#### Expected behaviour

![single_line_autocomplete.png](docs/single_line_autocomplete.png)

### Multi-line autocomplete

1. Paste the following Java code:
    ```java
    public void bubbleSort(int[] array) {
    ```
2. Place the cursor at the end of the line.
3. Trigger autocompletion with <kbd>Alt</kbd> + <kbd>/</kbd>.

#### Expected behaviour

![multiline_autocomplete.png](docs/multiline_autocomplete.png)

### Infilling autocomplete

1. Paste the following Java code:
    ```java
    // print 
    System.out.println("Hello World!");
    ```
2. Place cursor at the end of the `// print ` line.
3. Trigger autocompletion with <kbd>Alt</kbd> + <kbd>/</kbd>.

#### Expected behaviour

![multiline_autocomplete.png](docs/infilling_autocomplete.png)

### Cycle through autocomplete

1. Paste the following Java code:
    ```java
    public void bubbleSort(int[] array) {
    ```
2. Place the cursor at the end of the line.
3. Cycle forward with <kbd>Alt</kbd> + <kbd>]</kbd> or backward with <kbd>Alt</kbd> + <kbd>[</kbd>.

#### Expected behaviour

![cycle_through_autocomplete.gif](docs/cycle_through_autocomplete.gif)

## Commands

### General commands availability in Cody toolbar

1. Navigate to `Cody` toolbar and open `Commands`.

#### Expected behaviour

* List of commands is immediately available after the toolbar is displayed. **No refresh is required.**

### General commands availability in context menu

1. Open file with source code and select some fragment.
2. Show context menu with right mouse button, navigate to `Cody`, and choose one of the commands.

#### Expected behaviour

* All commands are visible in context menu and can be selected.
* All commands works after selection.

### Explain Selected Code (Detailed)

1. Paste the following Java code:
    ```java
    System.out.println("Hello, Cody!");
    ```
2. Select line and use `Cody | Commands | Explain Selected Code (Detailed)`.

#### Expected behaviour

* User is automatically switched to `Chat` tab.
* Chat responds with a **detailed** description of the selected code and will elaborate on the fields, classes, and
  methods, going into technical details, often structuring the text in bullet points.

### Explain Selected Code (High Level)

1. Paste the following Java code:
    ```java
    System.out.println("Hello, Cody!");
    ```
2. Select line and use `Cody | Commands | Explain Selected Code (Detailed)`.

#### Expected behaviour

* User is automatically switched to `Chat` tab.
* Chat responds with a **high-level** and a short description of the code without going into technical details.

### Generate Docstring

1. Paste following Java function:
    ```java
    public static String greet(String name) {
        return "Hello, " + name + "!";
    }
    ```
2. Select function and use `Cody | Commands | Generate Docstring`.

#### Expected behaviour

* User is automatically switched to `Chat` tab.
* Language has been identified as Java, so the documentation syntax is also in that language (see: `@param`
  and `@return` tags).
* Chat responds with generated docstring, similar to this:
    ```java
    /*
     * Returns a greeting string with the provided name.
     *
     * @param name The name to greet.
     * @return A greeting string.
     */
    ```

### Generate Unit Test

1. Paste following Java function:
    ```java
    public static String greet(String name) {
        return "Hello, " + name + "!";
    }
    ```
2. Select function and use `Cody | Commands | Generate Unit Test`.

#### Expected behaviour

* User is automatically switched to `Chat` tab.
* Chat responds with generated documentation similar to this:
    ```java
    @Test
    public void testGreet() {
      String result = greet("Alice");
      assertEquals("Hello, Alice!", result);
    }
    ```

### Improve Variable Names

1. Paste the following Java code:
    ```java
    String[] var0 = new String[]{"apple", "banana", "peach"};
    ```
2. Select line and use `Cody | Commands | Improve Variable Names`

#### Expected behaviour

* User is automatically switched to `Chat` tab.
* Chat responds with code similar to this:
    ```java
    String[] fruits = new String[]{"apple", "banana", "peach"};
    ```

### Smell Code

1. Paste the following Java code:
    ```java
    public static String greet(String name) {
        return "Hello, " + name + "!";
    }
    ```
2. Select line and use `Cody | Commands | Smell Code`

#### Expected behaviour

* User is automatically switched to `Chat` tab.
* Chat responds with **potential issues** and **suggestions** like missing nullability checks or input sanitization (
  response may vary).

## Chat

### Autoscroll to latest message

1. Fill the `Chat` with messages until the scrollbar appears.
2. Scroll all the way down.
3. Add new message.

#### Expected behaviour

* Scrollbar is **automatically** scrolled to the bottom. New message tokens are visible.

### Read chat history without interruptions

1. Fill the `Chat` with messages until the scrollbar appears.
2. Scroll up. Latest message should be not visible or partially visible.
3. Add new message.

#### Expected behaviour

* Scrollbar is **not moving automatically** while new message tokens are generated. You can easily read older messages
  without interruptions and scrolling is smooth.

## [Product-led growth](https://handbook.sourcegraph.com/departments/data-analytics/product-led-growth/)

### Cody Free/Pro subscription tab

1. Log in to Sourcegraph.com with a **Free** account and `cody-pro-jetbrains` feature flag enabled.
2. Go to the `Cody` toolbar and open `Subscription` tab.
3. Verify:
    * The current tier should be `Cody Free`.
    * The `Upgrade` button is visible and it points to `https://sourcegraph.com/cody/subscription`.
    * The `Check Usage` button is visible and it points to `https://sourcegraph.com/cody/manage`.
4. Go to accounts settings and switch to **Pro** account.
5. Go to `Subscription` tab.
6. Verify:
    * The current tier should be `Cody Pro`.
    * The `Upgrade` is **not visible**.
7. Go to account settings and switch to an Enterprise account (AKA *non-dotcom*).
8. Verify: `Subscription` panel is not visible in `Cody` toolbar.
9. Go to accounts settings and switch back to the **Free** account.
10. Verify: `Subscription` panel is visible.

### Cody Free/Pro rate limit errors

1. Log in to a Sourcegraph.com with a **Free account with rate limits exceeded**.
2. Go to the `Chat` and type a message.
3. Verify: A notification about the exceeded rate limit is shown. It should suggest upgrading to Cody Pro.
4. Trigger autocomplete in the editor.
5. Verify: A similar notification is shown to the user.

## Other

### Search Selection on Sourcegraph Web

1. Paste the following Java code:
    ```java
    System.out.println("Hello, Cody!");
    ```
2. Select `System.out.println` phrase.
3. Right-click on selection to open context menu and navigate to `Sourcegraph | Search Selection on Sourcegraph Web`.

#### Expected behaviour

* Browser automatically opened at `sourcegraph.com` with the `Code Search` feature. Results are found
  with `System.out.println` occurrences.

### Automatic repository recognition

1. Open project with enabled Git VCS. This repository must be publicly available on GitHub.
2. Open to `Cody` toolbar.
3. Click on repository button to open `Context Selection` dialog. Button is placed inside `Cody` toolbar on left, bottom
   corner.

#### Expected behaviour

* Repository `Git URL` has been successfully inferred from VCS history. Value is similar
  to `github.com/sourcegraph/jetbrains`.

### Persistent custom repository

1. Open project with enabled Git VCS. This repository must be publicly available on GitHub.
2. Open to `Cody` toolbar.
3. Click on repository button to open `Context Selection` dialog.
4. Change `Git URL` to a different, valid Git URL repository.
5. Click `OK` button and restart IDE.
6. Navigate again to `Context Selection`.

#### Expected behaviour

* Repository `Git URL` is same as before restart.

### Colour scheme changes

1. Ask Cody Chat question to which it will reply with various layout elements (list, code snippets, etc)
2. Change theme in settings or using Themes action.
3. Verify that chat text is readable, as well as that there is clear colour distinction between user and assistant
   sections. Both sections colors should also correspond to the chosen theme.

Repeat the above starting from different themes.

#### Expected behaviour

Changing theme should lead to full repaint of the colours according to the current theme.