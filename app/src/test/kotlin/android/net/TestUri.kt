package android.net

import android.os.Parcel

/**
 * A [Uri] for JVM tests, where the stubbed `Uri.parse` returns null. It lives in `android.net`
 * because [Uri]'s constructor is package-private. Only [toString] carries a value.
 */
class TestUri(private val value: String) : Uri() {
    override fun toString() = value
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getPort() = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getUserInfo(): String? = null
    override fun isHierarchical() = false
    override fun isRelative() = true
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    override fun equals(other: Any?) = other is TestUri && other.value == value
    override fun hashCode() = value.hashCode()
}
