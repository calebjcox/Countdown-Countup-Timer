package com.calebjcox.countdownwidgets.core

/**
 * How much of a say the cell gets over whether a row is drawn.
 *
 * The widget drops rows a cell has no room for, which is right almost always and is not
 * the user's answer to every question: someone who put a 1x1 on the home screen to see
 * one name may want that name more than they want the largest possible number, and
 * someone else never wants the row at whatever size. [WHEN_ROOM] is the middle answer and
 * the default, because it is the one that suits a widget that will be resized.
 *
 * Only [WHEN_ROOM] consults the cell. The other two are the point of the setting: they
 * are answers the cell cannot give.
 */
enum class RowVisibility { ALWAYS, WHEN_ROOM, NEVER }

/**
 * Whether the row is drawn, given whether the cell has room for it.
 *
 * [hasRoom] is the renderer's own breakpoint decision, so this is the whole of what the
 * setting adds on top of it.
 */
fun RowVisibility.shows(hasRoom: Boolean): Boolean = when (this) {
    RowVisibility.ALWAYS -> true
    RowVisibility.WHEN_ROOM -> hasRoom
    RowVisibility.NEVER -> false
}
