plugins {
    id("finsight.compose.library")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.neoutils.finsight.resources"
    generateResClass = always
}
