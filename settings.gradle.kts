import java.net.URI

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven( "https://jitpack.io")
    }
}

sourceControl {
    gitRepository(URI.create("https://github.com/material-components/material-components-android.git")) {
        producesModule("com.google.android.material:material-components-android")
    }
}

rootProject.name = "OpenTAK ICU"
include(":app")

includeBuild("/home/malolan/AndroidStudioProjects/RootEncoder") {
    dependencySubstitution {
        substitute(module("com.github.pedroSG94.RootEncoder:library")).using(project(":library"))
        substitute(module("com.github.pedroSG94.RootEncoder:extra-sources")).using(project(":extra-sources"))
    }
}