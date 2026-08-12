import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

/**
 * Zugangsdaten der Release-Signatur aus der nicht eingecheckten `keystore.properties` im Repo-Root,
 * siehe docs/decisions/0017-signatur-zugangsdaten-aus-keystore-properties.md. Die Vorlage mit den
 * erwarteten Schlüsseln steht in `keystore.properties.example`.
 *
 * Gelesen wird über `providers.fileContents`, damit der Configuration Cache die Datei als Eingabe
 * kennt (`org.gradle.configuration-cache=true` in gradle.properties). Fehlt sie, bleibt der Wert
 * `null` — der Signatur-Block entfällt dann und `assembleRelease` liefert eine unsignierte APK,
 * statt den ganzen Build zu blockieren.
 */
val keystoreProperties: Properties? = providers
    .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) } }
    .orNull

/** Muss mit den Schluesseln in `keystore.properties.example` uebereinstimmen. */
val REQUIRED_SIGNING_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

android {
    namespace = "eu.sweetgeorgie.browniedo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "eu.sweetgeorgie.browniedo"
        minSdk = 24
        targetSdk = 37
        // Steigt bei jeder mittleren oder größeren Änderung, nicht erst beim Verteilen — die Regel
        // samt Abgrenzung steht in AGENTS.md unter „Erwartungen an eine Änderung".
        versionCode = 7
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        keystoreProperties?.let { properties ->
            // Lieber hier mit einer klaren Ansage abbrechen als später beim Signieren mit einer
            // nichtssagenden Meldung. Der häufigste Fehler ist ein Backslash im `storeFile`-Pfad:
            // In einer .properties-Datei leitet er eine Escape-Sequenz ein, aus C:\Users wird
            // C:Users, und der Keystore ist „weg".
            val missing = REQUIRED_SIGNING_KEYS.filter { properties.getProperty(it).isNullOrBlank() }
            check(missing.isEmpty()) {
                "keystore.properties fehlen diese Eintraege: ${missing.joinToString()}. " +
                    "Vergleiche mit keystore.properties.example."
            }
            val store = rootProject.file(properties.getProperty("storeFile"))
            check(store.isFile) {
                "Der Keystore wurde nicht gefunden: $store. Pruefe storeFile in " +
                    "keystore.properties — Pfade dort mit / statt \\ schreiben, weil der " +
                    "Backslash in .properties-Dateien eine Escape-Sequenz einleitet."
            }

            create("release") {
                storeFile = store
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Ist keine keystore.properties da, gibt es auch keine Signatur-Konfiguration.
            // assembleRelease baut dann eine unsignierte APK, die sich nicht installieren laesst.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    @Suppress("AvoidDuplicateDependencies")
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // androidTestImplementation erbt nicht von implementation, die BOM wird hier also erneut
    // gebraucht. Siehe docs/decisions/0008-compose-bom-in-zwei-konfigurationen.md.
    @Suppress("AvoidDuplicateDependencies")
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}