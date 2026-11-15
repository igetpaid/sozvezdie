// Слой данных: Firebase (авторизация, база, файлы), Supabase (медиа) и Room (история на телефоне).
// Экраны не работают с сетью напрямую — только через репозитории этого модуля.
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Настройки Supabase. Сначала берём их из local.properties (если файл есть),
// иначе — из supabase.properties, который лежит в Git.
// Так проект собирается на любом компьютере без ручной настройки.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val sharedProperties = Properties().apply {
    val file = rootProject.file("supabase.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Значение из local.properties важнее значения из supabase.properties.
fun setting(name: String): String =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: sharedProperties.getProperty(name).orEmpty()

android {
    namespace = "com.sozvezdie.messenger.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${setting("SUPABASE_URL")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${setting("SUPABASE_ANON_KEY")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))

    // Firebase: вход, переписка в реальном времени, хранение файлов
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    // await() для задач Firebase — чтобы писать обычные suspend-функции
    implementation(libs.kotlinx.coroutines.play.services)

    // Room: локальная копия чатов и сообщений
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // OkHttp: загрузка фото, файлов и голосовых в Supabase Storage
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
