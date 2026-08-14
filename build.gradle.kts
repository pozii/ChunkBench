plugins {
    java
}

group = "com.pozii"
version = (findProperty("relVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "1.1.2"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.papermc.io/repository/maven-public/")
}

sourceSets {
    main {
        java.srcDirs("plugin/src/main/java")
        resources.srcDirs("plugin/src/main/resources")
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") {
        exclude(group = "net.md-5", module = "bungeecord-chat")
    }
    compileOnly("net.md-5:bungeecord-chat:1.16-R0.4")
}

tasks.jar {
    archiveBaseName.set("ChunkBench")
    archiveVersion.set(project.version.toString())
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
