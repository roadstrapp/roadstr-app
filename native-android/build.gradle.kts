val nativeBuildDirectory = rootProject.layout.projectDirectory.dir("../build/native-roadtest")
rootProject.layout.buildDirectory.value(nativeBuildDirectory)

subprojects {
    project.layout.buildDirectory.value(nativeBuildDirectory.dir(project.name))
}

tasks.register<Delete>("clean") {
    delete(nativeBuildDirectory)
}
