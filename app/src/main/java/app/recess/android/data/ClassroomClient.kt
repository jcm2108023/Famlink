package app.recess.android.data

import app.recess.core.FamilyJson
import app.recess.core.Gc
import app.recess.core.RemoteCourse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal read-only client for the Google Classroom REST API (v1). */
class ClassroomClient(private val accessToken: String) {

    class ApiException(val code: Int, message: String) : IOException(message)

    suspend fun me(): Gc.Profile = get("userProfiles/me", Gc.Profile.serializer())

    /**
     * Everything Recess needs for one student: their active courses (that the signed-in teacher or
     * admin can see), published coursework, the student's submissions, and a teacher name per course.
     */
    suspend fun fetchStudent(email: String): List<RemoteCourse> {
        val student = enc(email)
        val courses = paged { token ->
            get("courses?studentId=$student&courseStates=ACTIVE&pageSize=100${token.param()}", Gc.CourseList.serializer())
                .let { it.courses to it.nextPageToken }
        }
        return courses.map { course ->
            val id = enc(course.id)
            val work = paged { token ->
                get("courses/$id/courseWork?courseWorkStates=PUBLISHED&pageSize=100${token.param()}", Gc.CourseWorkList.serializer())
                    .let { it.courseWork to it.nextPageToken }
            }
            val submissions = if (work.isEmpty()) emptyList() else paged { token ->
                get("courses/$id/courseWork/-/studentSubmissions?userId=$student&pageSize=100${token.param()}", Gc.SubmissionList.serializer())
                    .let { it.studentSubmissions to it.nextPageToken }
            }
            val teacher = runCatching {
                get("courses/$id/teachers?pageSize=1", Gc.TeacherList.serializer()).teachers.firstOrNull()?.profile?.name?.fullName
            }.getOrNull()
            RemoteCourse(course, teacher, work, submissions)
        }
    }

    private suspend fun <T> paged(fetch: suspend (String?) -> Pair<List<T>, String?>): List<T> {
        val all = mutableListOf<T>()
        var token: String? = null
        do {
            val (items, next) = fetch(token)
            all += items
            token = next?.takeIf { it.isNotEmpty() }
        } while (token != null && all.size < MAX_ITEMS)
        return all
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val connection = URL("$BASE/$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(code, errorMessage(body) ?: "Classroom returned HTTP $code")
            FamilyJson.json.decodeFromString(serializer, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        FamilyJson.json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()

    private fun String?.param() = if (this == null) "" else "&pageToken=${enc(this)}"

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val BASE = "https://classroom.googleapis.com/v1"
        const val MAX_ITEMS = 1_000
    }
}
