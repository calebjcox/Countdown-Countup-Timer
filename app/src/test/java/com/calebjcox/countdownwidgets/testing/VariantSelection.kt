package com.calebjcox.countdownwidgets.testing

import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews

/**
 * Picks the `RemoteViews` variant a launcher would pick for a cell of a given size.
 *
 * `RemoteViews.getRemoteViewsToApply(Context, SizeF)` is public and has been since API
 * 31, but it is not in the SDK's public surface, so it is reached by reflection. There
 * is no hidden-API enforcement off-device. Going through the platform's own selection
 * code rather than reimplementing it is the point: the rule is not "the largest variant
 * that fits" but "the fitting variant nearest the cell by squared distance", and a
 * local copy of that would be free to agree with a wrong expectation.
 *
 * Plain `RemoteViews.apply` is not a substitute. It silently returns the *smallest*
 * variant, so a test built on it would report the compact layout for every size and
 * pass while the widget was broken.
 */
object VariantSelection {

    fun forSize(views: RemoteViews, context: Context, size: SizeF): RemoteViews {
        val method = runCatching {
            RemoteViews::class.java
                .getMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
        }.getOrElse {
            throw AssertionError("RemoteViews.getRemoteViewsToApply is no longer reachable", it)
        }
        return method.invoke(views, context, size) as RemoteViews
    }
}
