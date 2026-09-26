package app.recess.core

import kotlinx.serialization.Serializable

/**
 * Wire types for the Google Classroom REST API (v1), limited to the fields Recess reads.
 * Decode with [FamilyJson.json], which ignores unknown fields.
 */
object Gc {
    @Serializable data class Date(val year: Int? = null, val month: Int? = null, val day: Int? = null)
    @Serializable data class TimeOfDay(val hours: Int? = null, val minutes: Int? = null)
    @Serializable data class Name(val fullName: String? = null)
    @Serializable data class Profile(val id: String? = null, val name: Name? = null, val emailAddress: String? = null)
    @Serializable data class Teacher(val profile: Profile? = null)
    @Serializable data class Form(val formUrl: String? = null)
    @Serializable data class Material(val form: Form? = null)

    @Serializable
    data class Course(
        val id: String,
        val name: String = "",
        val section: String? = null,
        val room: String? = null,
        val courseState: String? = null,
    )

    @Serializable
    data class CourseWork(
        val id: String,
        val courseId: String = "",
        val title: String = "",
        val workType: String? = null,
        val state: String? = null,
        val dueDate: Date? = null,
        val dueTime: TimeOfDay? = null,
        val maxPoints: Double? = null,
        val materials: List<Material> = emptyList(),
    )

    @Serializable
    data class Submission(
        val id: String,
        val courseWorkId: String,
        val userId: String? = null,
        val state: String? = null,
        val assignedGrade: Double? = null,
        val updateTime: String? = null,
    )

    @Serializable data class CourseList(val courses: List<Course> = emptyList(), val nextPageToken: String? = null)
    @Serializable data class CourseWorkList(val courseWork: List<CourseWork> = emptyList(), val nextPageToken: String? = null)
    @Serializable data class SubmissionList(val studentSubmissions: List<Submission> = emptyList(), val nextPageToken: String? = null)
    @Serializable data class TeacherList(val teachers: List<Teacher> = emptyList(), val nextPageToken: String? = null)
}

/** Everything fetched from Classroom for one course of one child. */
data class RemoteCourse(
    val course: Gc.Course,
    val teacherName: String?,
    val work: List<Gc.CourseWork>,
    val submissions: List<Gc.Submission>,
)
