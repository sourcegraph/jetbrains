## Checklist

- Onboarding
  - [ ] Sign in with GitHub
  - [ ] Sign in with GitLab
  - [ ] Sign in with Google
- Autocomplete
  - [ ] Single-line autocomplete
  - [ ] Multi-line autocomplete
  - [ ] Infilling autocomplete
  - [ ] Cycle through autocomplete
- Commands
  - [ ] TODO
- Chat
  - [ ] TODO

## Onboarding

### Sign in with GitHub

TODO

### Sign in with GitLab

TODO

### Sign in with Google

TODO

## Autocomplete

### Single-line autocomplete

1. Paste following Java code:
    ```
    // print Hello World!
    System.out.
    ```
2. Place cursor at the end of the `System.out.` line.
3. Trigger autocompletion with <kbd>Alt</kbd> + <kbd>/</kbd>.

Expected behaviour:

![single_line_autocomplete.png](docs/single_line_autocomplete.png)

### Multi-line autocomplete

1. Paste following Java code:
    ```
    public void bubbleSort(int[] array) {
    ```
2. Place the cursor at the end of the line.
3. Trigger autocompletion with <kbd>Alt</kbd> + <kbd>/</kbd>.

Expected behaviour:

![multiline_autocomplete.png](docs/multiline_autocomplete.png)

### Infilling autocomplete

1. Paste following Java code:
    ```
    // print 
    System.out.println("Hello World!");
    ```
2. Place cursor at the end of the `// print ` line.
3. Trigger autocompletion with <kbd>Alt</kbd> + <kbd>/</kbd>.

Expected behaviour:

![multiline_autocomplete.png](docs/infilling_autocomplete.png)

### Cycle through autocomplete

1. Paste following Java code:
    ```
    public void bubbleSort(int[] array) {
    ```
2. Place the cursor at the end of the line.
3. Cycle forward with <kbd>Alt</kbd> + <kbd>]</kbd> or backward with <kbd>Alt</kbd> + <kbd>[</kbd>.

4. Expected behaviour:

![cycle_through_autocomplete.gif](docs/cycle_through_autocomplete.gif)

## Commands

TODO

## Chat

TODO
