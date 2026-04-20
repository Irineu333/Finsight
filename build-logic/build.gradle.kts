plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(files(libs::class.java.protectionDomain.codeSource.location))

    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.android.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.room.gradlePlugin)
}
