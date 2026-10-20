/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */





package unshoo.ianshulyadav.pixelmusic.innertube.models.body

import unshoo.ianshulyadav.pixelmusic.innertube.models.Context
import unshoo.ianshulyadav.pixelmusic.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String? = null,
    val formData: FormData? = null
) {
    @Serializable
    data class FormData(
        val selectedValues: List<String>
    )
}
