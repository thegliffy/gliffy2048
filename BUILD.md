// Deployment baseline for ./gradlew (run from project root).
# No wrapper distribution needed: this box already has Gradle 8.7 + a warm
# caches/modules-2 dir from a prior build; invoke the system Gradle directly:
#
#   JAVA_HOME=~/android-toolchain/jdk-17.0.20+8 \
#   ANDROID_HOME=~/Android/Sdk \
#   ~/android-toolchain/gradle-8.7/bin/gradle -p /home/gliffy/android-2048 :app:assembleDebug
#
# AGP 8.5.2 is cached under ~/.gradle (AAPT2 8.5.2-11315950, apksig 8.5.2).
# Kotlin 2.0.21 + Compose 1.7.5 + Material3 1.3.1 all present in local cache.
