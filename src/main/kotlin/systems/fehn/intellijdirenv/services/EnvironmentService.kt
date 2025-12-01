package systems.fehn.intellijdirenv.services

import com.intellij.openapi.components.Service
import systems.fehn.intellijdirenv.MyBundle

@Service
class EnvironmentService {
    // Track variables loaded by direnv (separate from System.getenv modifications)
    private val loadedVariables = mutableMapOf<String, String>()

    fun unsetVariable(name: String): Boolean {
        val existed = modifiableEnvironment.containsKey(name)
        modifiableEnvironment.remove(name)
        loadedVariables.remove(name)
        return existed
    }

    fun setVariable(name: String, value: String): Boolean {
        val changed = modifiableEnvironment[name] != value
        modifiableEnvironment[name] = value
        loadedVariables[name] = value
        return changed
    }

    /**
     * Returns a copy of all variables that were loaded by direnv.
     * Used by GradleEnvironmentProvider to pass env vars to Gradle Tooling API.
     */
    fun getLoadedVariables(): Map<String, String> = loadedVariables.toMap()

    class ManipulateEnvironmentException(message: String) : Throwable(message)

    private val modifiableEnvironment by lazy {
        val env = System.getenv()
        val envClass = env.javaClass

        when (envClass.canonicalName) {
            "java.util.Collections.UnmodifiableMap" -> hackUnmodifiableMap(env, envClass)

            else -> throw ManipulateEnvironmentException(MyBundle.message("exception.unknownClass", envClass.name))
        }
    }

    private fun hackUnmodifiableMap(obj: Any, cls: Class<*>): MutableMap<String, String> {
        val mapFields = cls.declaredFields.filter { it.type == Map::class.java }

        val mapField = when (mapFields.size) {
            0 -> throw ManipulateEnvironmentException(
                MyBundle.message(
                    "exception.noMapField",
                    cls.canonicalName,
                    Map::class.java.canonicalName
                )
            )
            1 -> mapFields[0]
            else -> throw ManipulateEnvironmentException(
                MyBundle.message(
                    "exception.multipleMapFields",
                    cls.canonicalName,
                    Map::class.java.canonicalName,
                    mapFields.map { it.name }
                )
            )
        }

        mapField.isAccessible = true
        val map = mapField.get(obj)
        @Suppress("UNCHECKED_CAST")
        return map as MutableMap<String, String>
    }
}
