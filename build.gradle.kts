plugins {
    java
}

group = "fr.merchanttalk"
version = "1.3.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.fancyinnovations.com/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.135-stable")
    compileOnly("de.oliver:FancyNpcs:2.11.0") { isTransitive = false }
    compileOnly("com.github.Gypopo:EconomyShopGUI-API:1.11.0") { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    // La licence est incluse dans le jar.
    from("LICENSE")
}
