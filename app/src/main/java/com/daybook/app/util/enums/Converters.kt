package com.daybook.app.util.enums

import androidx.room.TypeConverter
import com.daybook.app.data.model.ColorTag
import com.daybook.app.ui.theme.AccentColor

/**
 * Room type converters. Only the enum <-> String pairs are live — no entity has a
 * `List<LocalTime>` / `List<DayOfWeek>` field (times/days are stored as `*_json` String
 * columns and parsed via [com.daybook.app.util.DateTimeUtils]), so the former
 * list-JSON converters were dead and removed (REV-33).
 */
object Converters {
    @TypeConverter
    fun colorTagToString(tag: ColorTag?): String = (tag ?: ColorTag.AUTO).name

    @TypeConverter
    fun stringToColorTag(name: String?): ColorTag = ColorTag.fromNameOrAuto(name)

    @TypeConverter
    fun accentColorToString(accent: AccentColor?): String = (accent ?: AccentColor.DEFAULT).storageKey

    @TypeConverter
    fun stringToAccentColor(key: String?): AccentColor = AccentColor.fromKey(key)
}
