# Direnv Enhanced - direnv integration for JetBrains IDEs

> **Fork of [Direnv Integration](https://github.com/fehnomenal/intellij-direnv) by fehnomenal with additional features.**

## What's New in Direnv Enhanced

- **WSL Support**: Full support for projects located in WSL (Windows Subsystem for Linux)
- **Monorepo Support**: Automatically finds `.envrc` files in parent directories
- **Gradle Integration**: Environment variables are injected into all Gradle operations (sync, build, tests, run)
- **Improved Stability**: Fixed process deadlock issues and added timeouts

---

<!-- Plugin description -->
This plugin provides an action to import environment variables from [direnv](https://github.com/direnv/direnv) into the Java process that is running the IDE.

### Automatic Import before every Run/Debug
To automatically load the environment variables from a `<project_root>/.envrc` file before each and every execution of a Run/Debug configuration, visit <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>Direnv Settings</kbd> and tick the relevant checkbox.

On starting a Run/Debug job, a notification will show if the existing environment has been changed. The newly spawned process which executes the Run/Debug configuration will inherit the environment. If you often work with multiple project windows open in a single IDE instance, this is probably the best option for you.

### Automatic Import on Startup
To automatically load the environment variables from a `<project_root>/.envrc` file when you open the project, visit <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>Direnv Settings</kbd> and tick the relevant checkbox.

If "Automatic Import on Startup" is disabled, a popup notification will appear whenever a project with a `.envrc` file in the root is opened. You can load the `.envrc` file by clicking on the link in the notification. 

### Manual Import
To manually load an `.envrc` file: 
- If you have the main toolbar enabled (<kbd>View</kbd> > <kbd>Appearance</kbd> > <kbd>Main Toolbar</kbd>), a button next to the <kbd>Reload All from Disk</kbd> action will start the process.

![action-icon](https://user-images.githubusercontent.com/9959940/98688979-b6c88700-236b-11eb-8e27-319f23376212.png)

- You can also right-click anywhere in the project and click <kbd>Import Direnv</kbd> to reload the environment.

**Note**: This plugin automatically finds `.envrc` files in parent directories, which is useful for monorepo setups.

**Note**: You need `direnv` in your path, or you can specify the location of your direnv executable in <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>Direnv Settings</kbd>

<!-- Plugin description end -->

## Installation

- Manually:

  Download the [latest release](https://github.com/ludosch/direnvenhanced/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## Building from source

```shell script
git clone https://github.com/ludosch/direnvenhanced
cd direnvenhanced
./gradlew buildPlugin
```

The plugin is now in the folder `build/distributions/` and can be installed manually.


##### On NixOS

The gradle plugins downloads JetBrains' JRE and fails to execute it.
Add the following in `build.gradle.kts`:
```kotlin
tasks.withType<org.jetbrains.intellij.tasks.RunIdeBase> {
    projectExecutable.set(org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath)
}
```

---

Logo and icon source: https://github.com/direnv/direnv-logo/tree/0949c12bafa532da0b23482a1bb042cf41b654fc

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
