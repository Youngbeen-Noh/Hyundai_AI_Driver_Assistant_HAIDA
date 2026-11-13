pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK (Vehicle / NaviHelper / TextToSpeech / SpeechToText / FusedLocation). 로그인 없이 받을 수 있다.
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "PleosAiAssistant"
include(":app")
// 테스트용 가짜 멜론(에뮬레이터에 멜론이 없어 음악 앱 연결을 확인하려고). 차량에 올리지 않는다
include(":fake-melon")
project(":fake-melon").projectDir = file("tools/fake-melon")
