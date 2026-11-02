# Maven repository mode

`maven.properties` configures the default repository mode and local repository
path, while command-line overrides (`-Pproject.repositories.mavenLocal` and
`-Dmaven.repo.local`) take precedence when supplied (for example via `localbuild`).

The template starts with:

```properties
maven.local=true
maven.localRepository=
```

With `maven.local=true` (or `-Pproject.repositories.mavenLocal=true`), Gradle tries
Maven Local first for project dependencies, buildscript dependencies, and plugin
resolution, then falls back to the configured remote repositories. With
`maven.local=false` (or `-Pproject.repositories.mavenLocal=false`), resolution uses
remote repositories without consulting Maven Local. This switch does not enable
Gradle offline mode.

An empty `maven.localRepository` selects `<user.home>/.m2/repository` unless
`-Dmaven.repo.local=/absolute/path` is passed on the command line. Use forward
slashes in Windows paths, for example `C:/Users/name/.m2/repository`.

```bash
./gradlew publishToMavenLocal -Pproject.repositories.mavenLocal=true -Dmaven.repo.local=$HOME/.m2/repository
./gradlew publishToMavenLocal
./gradlew build
```

Publication tasks use standard Gradle `maven-publish` behavior: `publishToMavenLocal`
stages and installs publications directly to the configured local Maven repository,
and `publishToCentralPortal` (`publishAndReleaseToMavenCentral`) packages and uploads
the Central Portal deployment bundle.
