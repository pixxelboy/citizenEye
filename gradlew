#!/bin/sh
APP_HOME=$(cd "${0%/*}" >/dev/null 2>&1; pwd -P)
JAVA_EXE="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_EXE" ]; then JAVA_EXE=java; fi
exec "$JAVA_EXE" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
