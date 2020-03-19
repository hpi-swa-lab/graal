# Babylonian Programming in the GraalVM

## Setup

This setup guide was written primarily for macOS. Make sure to have the macOS SDK Headers installed before starting. You can do this e.g. on macOS 10.14 by running the following command `open /Library/Developer/CommandLineTools/Packages/macOS_SDK_headers_for_macOS_10.14.pkg`.

SimpleLanguage support is a little bit ugly and therefore not yet merged. If you want to have SL support, checkout `feature/simple-language-support` ([Tag v1.0.1](https://github.com/hpi-swa-lab/MPWS2019RH1/releases/tag/v1.0.1)) else use the `master` ([Tag v1.0.0](https://github.com/hpi-swa-lab/MPWS2019RH1/releases/tag/v1.0.0)). The PR for SL support is here: https://github.com/hpi-swa-lab/MPWS2019RH1/pull/123

### Download JVMCI-enabled JDK 8 for your OS

- (don't use Java 11 since setting boot classpath as JVM argument doesn't work anymore in Java 11 which we rely on to make one of our instruments discoverable)
- ... from here: https://github.com/graalvm/openjdk8-jvmci-builder/releases (Tested with [Version 19.3-b07](https://github.com/graalvm/openjdk8-jvmci-builder/releases/tag/jvmci-19.3-b07))
- Extract to a known location
- `export JAVA_HOME=/absolute/path/to/jvmci-jdk/Contents/Home` for GraalVM to find it

### Build GraalVM

- Create a new clean directory for the project
  ```
  mkdir babylonian_ls
  cd babylonian_ls
  ```
- Clone necessary repositories into new clean directory
  ```
  git clone https://github.com/hpi-swa-lab/MPWS2019RH1.git --depth=1
  git clone https://github.com/graalvm/mx.git --depth=1  # tested with f0494f43
  git clone --depth 1 https://github.com/Kolpa/graaljs.git -b bugfix/get-argument-name-without-frame  # this can be replaced later with the original graaljs once the fix is merged: https://github.com/graalvm/graaljs/pull/238
  git clone https://github.com/graalvm/graalpython.git --depth=1  # tested with fe55db2b
  ```
- Set necessary env variables
  ```
  export SDKROOT="$(xcrun --show-sdk-path)"
  export LANG=en_US.UTF-8
  export LC_ALL=en_US.UTF-8
  ```
- Make sure `clang -v` shows the path to your `CommandLineTools` as `InstalledDir`. If this is not the case, you might have a custom version of `llvm` installed, e.g. via brew, which you might want to uninstall.
- Build GraalVM
  ```
  cd MPWS2019RH1/vm
  # to have a version of our project which includes support for SimpleLanguage, `git checkout feature/simple-language-support` !
  ../../mx/mx --dy /compiler,/graal-nodejs,/graalpython,/tools build
  ```
- Now a symlink to the built GraalVM `/Contents/Home` will be available at `MPWS2019RH1/vm/latest_graalvm_home`.

#### Troubleshooting

If you get an error `bin/capture.d:1: *** multiple target patterns. Stop.` when building `com.oracle.truffle.llvm.tests.pipe.native` as part of the mx build command, try removing this package from the build process by applying this diff to your `MPWS2019RH1` repo:

```
diff --git a/sulong/mx.sulong/suite.py b/sulong/mx.sulong/suite.py
index 364b69e..6f39c85 100644
--- a/sulong/mx.sulong/suite.py
+++ b/sulong/mx.sulong/suite.py
@@ -459,26 +459,6 @@ suite = {
       "defaultBuild" : False,
     },

-    "com.oracle.truffle.llvm.tests.pipe.native" : {
-      "subDir" : "tests",
-      "native" : True,
-      "vpath" : True,
-      "results" : [
-        "bin/<lib:pipe>",
-      ],
-      "buildDependencies" : [
-        "com.oracle.truffle.llvm.tests.pipe",
-      ],
-      "buildEnv" : {
-        "CPPFLAGS" : "-I<jnigen:com.oracle.truffle.llvm.tests.pipe>",
-        "LIBPIPE" : "<lib:pipe>",
-        "OS" : "<os>",
-      },
-      "checkstyle" : "com.oracle.truffle.llvm.runtime",
-      "license" : "BSD-new",
-      "testProject" : True,
-      "jacoco" : "exclude",
-    },
     "com.oracle.truffle.llvm.libraries.bitcode" : {
       "subDir" : "projects",
       "native" : True,
@@ -1124,7 +1104,6 @@ suite = {
       "platformDependent" : True,
       "output" : "mxbuild/<os>-<arch>/sulong-test-native",
       "dependencies" : [
-        "com.oracle.truffle.llvm.tests.pipe.native",
         "com.oracle.truffle.llvm.tests.native",
       ],
       "license" : "BSD-new",
```

### Add support for SimpleLanguage to your GraalVM

- Checkout simplelanguage repo
  ```
  cd babylonian_ls
  git clone https://github.com/graalvm/simplelanguage.git --depth=1  # tested with 9244b529
  ```
- Set necessary env variables
  ```
  export JAVA_HOME=/path/to/latest_graalvm_home  # be aware that this conflicts with the JAVA_HOME env var set above to build GraalVM itself
  export PATH=/path/to/latest_graalvm_home/bin:$PATH
  export SL_BUILD_NATIVE=false
  ```
- Build SimepleLanguage
  ```
  cd simplelanguage
  mvn package
  ```
- Add SimpleLanguage to your custom-built GraalVM as a component (since SimpleLanguage apparently can't be built together with the GraalVM right away). _You need to repeat this step everytime after rebuilding your GraalVM in order to have SL support!_
  ```
  /path/to/latest_graalvm_home/bin/gu -L install -f ./component/sl-component.jar
  ```

### Setting up your dev environment in IntelliJ to debug the Babylonian Language Server (GraalVM tools package)

```
cd MPWS2019RH1/tools
../../mx/mx ideinit --no-python-projects
idea .  # to open the tools dir with IntelliJ
```

Now a default debug config will be available in IntelliJ to debug the custom-built GraalVM's `tools` package when you open it as a project. Complete the setup in IntelliJ by setting File > Project Structure > Project SDK to `latest_graalvm_home`.

### Debug the Babylonian Language Server

- Open the `MPWS2019RH1/tools` dir in IntelliJ with `idea MPWS2019RH1/tools` if you don't have it open yet
- `cd MPWS2019RH1/vscode/graalvm` and `npm install`
- Open the GraalVM VS Code extension in VS Code with `code .`
- Hit `F5` in VS Code to debug the extension
- A new window will open which has the title "Extension Development Host"
- At this point, make sure that there are no files open in the Extension Development Host as this might lead to undefined behavior because the language server doesn't expect any files to be open initially.
- When you first debug the extension, a pop up will appear letting you set the path to your GraalVM home, use `latest_graalvm_home` for this as described above
- In the Extension Development Host, open a project directory (not a single file), e.g. the directory containing the code sample for the Babylonian LS
- Once a pop up with title "Click when ready to debug" appears, hit debug in the `tools` project open in IntelliJ
- Then, click "Debug Connect" inside the popup in the Extension Development Host window
- Now you can open a file from the open project in VS Code and the Babylonian language server features will be active
- If you've set a breakpoint in IntelliJ, it will stop the application once it's hit now
