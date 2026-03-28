# Referência: `multiplatform-settings`

Biblioteca para persistência de chave-valor em Kotlin Multiplatform.

**Versão:** `1.3.0`
**Repositório:** `com.russhwolf:multiplatform-settings`

---

## Dependências no projeto

O projeto usa o construtor `Settings()` sem argumento (no-arg). São necessárias **duas** dependências:

```kotlin
// composeApp/build.gradle.kts — ambas já presentes no projeto
implementation("com.russhwolf:multiplatform-settings:1.3.0")
implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")
```

A variante `no-arg` habilita `Settings()` usando os padrões da plataforma:
- **Android**: `SharedPreferences` (via `androidx-startup` para injetar `Context`)
- **iOS/macOS**: `NSUserDefaults`
- **Desktop/JVM**: `java.util.prefs.Preferences`

---

## Uso no projeto

O `Settings` já é registrado como singleton no DI:

```kotlin
// di/RepositoryModule.kt
single<Settings> { Settings() }
```

Repositórios recebem via injeção:

```kotlin
class DashboardPreferencesRepository(
    private val settings: Settings,
) : IDashboardPreferencesRepository
```

---

## API — Escrita

```kotlin
settings.putString("key", "value")
settings.putInt("key", 42)
settings.putBoolean("key", true)
settings.putLong("key", 100L)
settings.putDouble("key", 3.14)
settings.putFloat("key", 1.5f)
```

---

## API — Leitura

```kotlin
// Com valor default (nunca lança exceção)
val value: String = settings.getString("key", defaultValue = "")
val value: Int = settings.getInt("key", defaultValue = 0)

// Nullable (retorna null se ausente)
val value: String? = settings.getStringOrNull("key")
val value: Int? = settings.getIntOrNull("key")
```

**Para `DashboardPreferencesRepository`, usar `getStringOrNull`:**

```kotlin
private fun load(): List<DashboardComponentPreference> {
    val json = settings.getStringOrNull(KEY) ?: return emptyList()
    // ...
}
```

---

## API — Outros

```kotlin
val exists: Boolean = settings.hasKey("key")
settings.remove("key")
settings.clear()               // remove todas as keys
val keys: Set<String> = settings.keys
val size: Int = settings.size
```

---

## Serialização JSON para `Map<String, String>` e listas

`Settings` não suporta objetos complexos diretamente. Usar `kotlinx.serialization` para serializar para String:

```kotlin
@Serializable
private data class SerializablePreference(
    val key: String,
    val position: Int,
    val config: Map<String, String> = emptyMap(),
)

// Salvar
val json = Json.encodeToString(preferences.map { it.toSerializable() })
settings.putString(KEY, json)

// Carregar
val json = settings.getStringOrNull(KEY) ?: return emptyList()
val list = Json.decodeFromString<List<SerializablePreference>>(json)
```

`Json` de `kotlinx.serialization` está disponível no projeto. Usar `Json { ignoreUnknownKeys = true }` para tolerância a mudanças futuras no schema.

---

## Observabilidade

`Settings` básico (não-observable) não emite flows. O `DashboardPreferencesRepository` usa um `MutableStateFlow` interno para bridging:

```kotlin
class DashboardPreferencesRepository(
    private val settings: Settings,
) : IDashboardPreferencesRepository {

    private val _preferences = MutableStateFlow(load())

    override fun observe(): Flow<List<DashboardComponentPreference>> = _preferences

    override suspend fun save(preferences: List<DashboardComponentPreference>) {
        val json = Json.encodeToString(preferences.map { it.toSerializable() })
        settings.putString(KEY, json)
        _preferences.value = preferences   // notifica observers
    }
}
```

**Nota:** `ObservableSettings` (variante com listeners nativos) existe mas não é necessário aqui — o `MutableStateFlow` é suficiente e mais idiomático em KMP com Coroutines.

---

## Testes

Para testes unitários, substituir `Settings` por `MapSettings` (em memória):

```kotlin
// build.gradle.kts (commonTest)
testImplementation("com.russhwolf:multiplatform-settings-test:1.3.0")

// No teste:
val settings: Settings = MapSettings()
val repository = DashboardPreferencesRepository(settings)
```