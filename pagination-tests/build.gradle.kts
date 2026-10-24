plugins { kotlin("jvm") version "2.4.21" }
repositories { mavenCentral() }
sourceSets.main {
    kotlin.srcDir("../app_fenrir/src/main/kotlin")
    kotlin.include("dev/ragnarok/fenrir/fragment/comments/TopicCommentsPager.kt")
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }
