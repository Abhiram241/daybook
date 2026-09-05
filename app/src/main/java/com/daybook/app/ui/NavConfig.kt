package com.daybook.app.ui

/**
 * Pure helpers for the configurable bottom-nav (rec 7 / SD-2).
 *
 * `nav_tabs` is an ordered CSV of route ids. Today (`"home"`) is ALWAYS present and ALWAYS first
 * — that preserves the "index 0 == Today" invariant that `BackHandler` and the deep-link
 * fallbacks in `MainActivity` rely on. Reorder is deliberately NOT implemented this round; the CSV
 * is ordered anyway so it can be added later with no migration.
 */
object NavConfig {

    /** The three known top-level route ids, in their canonical order. */
    val ALL_ROUTES = listOf("home", "routines", "foodmed")

    /**
     * Resolve the stored `nav_tabs` CSV to the list of visible route ids:
     *  - keep only the known ids, in their stored order;
     *  - force Today (`"home"`) present and first;
     *  - a blank / all-unknown value falls back to all three.
     */
    fun visibleRoutesFrom(csv: String?): List<String> {
        val stored = csv.orEmpty().split(",").map { it.trim() }.filter { it in ALL_ROUTES }
        if (stored.isEmpty()) return ALL_ROUTES
        val rest = stored.filter { it != "home" }.distinct()
        return (listOf("home") + rest)
    }

    /**
     * Pager index for a stored landing-tab route id within [visibleRoutes]. A hidden / unknown id
     * falls back to 0 (Today).
     */
    fun landingIndex(routeId: String?, visibleRoutes: List<String>): Int =
        visibleRoutes.indexOf(routeId).coerceAtLeast(0)

    /** Toggle a route id in a `nav_tabs` CSV, preserving order. `"home"` can never be removed. */
    fun toggleRoute(csv: String?, route: String): String {
        if (route !in ALL_ROUTES) return csv.orEmpty()
        val current = visibleRoutesFrom(csv).toMutableList()
        if (route == "home") return current.joinToString(",")
        if (!current.remove(route)) {
            // Re-insert in canonical order so a re-enabled tab lands in its natural slot.
            val insertAt = current.indexOfFirst { ALL_ROUTES.indexOf(it) > ALL_ROUTES.indexOf(route) }
            if (insertAt < 0) current.add(route) else current.add(insertAt, route)
        }
        return current.joinToString(",")
    }
}
