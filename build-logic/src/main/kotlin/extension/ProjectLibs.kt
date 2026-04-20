import org.gradle.api.Project
import org.gradle.accessors.dm.LibrariesForLibs

internal val Project.libs: LibrariesForLibs
    get() = extensions.getByType(LibrariesForLibs::class.java)
