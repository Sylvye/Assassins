plugins { java }

group = "dev.tigermce"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.test { useJUnitPlatform() }
tasks.processResources { filesMatching("plugin.yml") { expand("version" to project.version) } }
