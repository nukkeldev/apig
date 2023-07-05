import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import util.formatAsParameterName
import util.logger
import util.toTemplate
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.writeText

object OpenAPIParser {
    private val logger = OpenAPIParser.logger()

    val json = Json {
        ignoreUnknownKeys = true
    }
    private val client = HttpClient(CIO)

    suspend fun parseFromURL(url: String): OpenAPI {
        logger.info("Downloading $url...")
        val response = client.get(url)
        logger.info("Parsing $url into an object per the OpenAPI 3.0.3 spec...")
        val res = json.decodeFromString<OpenAPI>(response.bodyAsText())
        logger.info("Successfully parsed $url!")
        return res
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseFromFile(path: String): OpenAPI {
        logger.info("Parsing $path into an object per the OpenAPI 3.0.3 spec...")
        val inputStream = File(path).inputStream()
        val api = json.decodeFromStream<OpenAPI>(inputStream)
        inputStream.close()
        logger.info("Successfully parsed $path!")
        return api
    }
}

val logger = "Main".logger()

fun main(args: Array<String>) {
    val api = runBlocking {
        async {
            OpenAPIParser.parseFromURL("https://www.thebluealliance.com/swagger/api_v3.json")
        }.await()
    }

    val apiInfo = api.info
    val title = apiInfo.title.formatAsParameterName()

    val outputDirectory = Path("src/main/kotlin/example/$title").toAbsolutePath()
    if (outputDirectory.notExists()) outputDirectory.createDirectory()
    val schemaDirectory = Path(outputDirectory.toString(), "schemas").toAbsolutePath()
    if (schemaDirectory.notExists()) schemaDirectory.createDirectory()

    api.outputDirectory = outputDirectory.toString()
    api.basePackage = "example.$title"

    logger.info("Generating API in $outputDirectory...")

    val paths = mutableMapOf<String, Path>()
    for ((s, path) in api.paths) {
        val key = s.substring(1)

        val obj = Path(
            url = s,
            pathItemObject = path
        )

        val routes = key.split("/")
        var map = paths
        for (idx in 0 until routes.size - 1) {
            if (map[routes[idx]] == null) {
                map[routes[idx]] = Path()
            }
            map = map[routes[idx]]?.children!!
        }

        routes.last().let { last ->
            map[last]?.let {
                obj.children = it.children
            }
            if (routes.last().startsWith("{") && routes.last().endsWith("}"))
                obj.parameter = routes.last()
        }

        map[routes.last()] = obj

    }

    val root = Path(
        url = "/",
        children = paths
    )

    logger.debug("${api.paths.size} paths defined:")
    logger.debug("Tree:\n${root.print()}")

    val rootFile = Path(api.outputDirectory!!, "API.kt")
    if (rootFile.notExists()) rootFile.createFile()

    val servers = api.servers?.joinToString(",\n") { s ->
        s.format()
    } ?: ""

    val endpoints = root.children.map {
        it.value.writeClass(it.key, api)
    }.joinToString("\n\n")
        .lines()
        .mapIndexed { index, s -> if (index > 0) s.prependIndent("\t") else s }
        .joinToString("\n")

    val rootTemplate = """
        @file:Suppress("unused", "UNUSED_PARAMETER")

        package %package%
        
        import io.ktor.client.*
        import io.ktor.client.engine.cio.*
        import io.ktor.client.request.*
        import io.ktor.client.statement.*
        import io.ktor.http.*
        import io.ktor.util.*
        import kotlinx.serialization.json.Json
        import org.slf4j.LoggerFactory
        
        /**
         * ### %title% \[v%version%\]
         * %~hasDescription -> "%description%"~%
         */
        object API {
            private val servers = listOf(
                %servers%
            )
            
            private val client = HttpClient(CIO)
            private val logger = LoggerFactory.getLogger(this::class.java)
            
            private const val API_KEY = <API_KEY_HERE>
        
            private suspend inline fun <reified T> getURL(
                url: String,
                expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
                parameters: Map<String, String?> = mapOf()
            ): T? {
                logger.info("Getting ${"\$"}url...")
                val response = client.get(servers[0] + url) {
                    headers {
                        append("X-TBA-Auth-Key", API_KEY) // TODO: AUTH IN TEMPLATE
                        append("accept", "application/json") // TODO: WHATEVER THIS IS
                        parameters.entries.filter { (_, v) -> v != null }.forEach { append(it.key, it.value!!) }
                    }
                }
                if (response.status != expectedStatusCode) {
                    logger.error("Expected ${"\$"}expectedStatusCode, but got status code ${"\$"}{response.status} when getting ${"\$"}url")
                }
                return Json.decodeFromString<T>(response.bodyAsText())
            }
            
            %endpoints%
        }
    """.trimIndent().toTemplate()

    rootFile.writeText(
        text = rootTemplate.build(
            mapOf(
                "package" to api.basePackage!!,
                "title" to apiInfo.title,
                "version" to apiInfo.version,
                "hasDescription" to apiInfo.description.isNullOrEmpty().toString(),
                "description" to apiInfo.description?.replace("\n", "\n * ")!!,
                "servers" to servers,
                "endpoints" to endpoints,
            )
        )
    )
}