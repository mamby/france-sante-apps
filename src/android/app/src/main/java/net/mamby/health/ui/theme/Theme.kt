package net.mamby.health.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.UUID

@Immutable
internal data class ProfileAccentColors(
    val container: Color,
    val onContainer: Color,
)

@Immutable
internal class ProfileAccentPalette(
    private val accents: List<ProfileAccentColors>,
) {
    fun colorsFor(profileId: UUID): ProfileAccentColors {
        val mixedBits = (profileId.mostSignificantBits xor profileId.leastSignificantBits).let {
            it xor (it ushr Int.SIZE_BITS)
        }
        return accents[Math.floorMod(mixedBits.toInt(), accents.size)]
    }
}

@Immutable
internal data class HomeTileColors(
    val container: Color,
    val content: Color,
)

internal enum class HomeTileTone {
    YELLOW,
    MINT,
    SKY,
    LAVENDER,
    CORAL,
    PEACH,
    AQUA,
}

@Immutable
internal data class HomeTilePalette(
    val yellow: HomeTileColors,
    val mint: HomeTileColors,
    val sky: HomeTileColors,
    val lavender: HomeTileColors,
    val coral: HomeTileColors,
    val peach: HomeTileColors,
    val aqua: HomeTileColors,
) {
    fun colorsFor(tone: HomeTileTone): HomeTileColors = when (tone) {
        HomeTileTone.YELLOW -> yellow
        HomeTileTone.MINT -> mint
        HomeTileTone.SKY -> sky
        HomeTileTone.LAVENDER -> lavender
        HomeTileTone.CORAL -> coral
        HomeTileTone.PEACH -> peach
        HomeTileTone.AQUA -> aqua
    }
}

private val LightProfileAccents = ProfileAccentPalette(
    accents = listOf(
        ProfileAccentColors(ProfileBlueLight, OnProfileBlueLight),
        ProfileAccentColors(ProfilePurpleLight, OnProfilePurpleLight),
        ProfileAccentColors(ProfileOrangeLight, OnProfileOrangeLight),
        ProfileAccentColors(ProfileRoseLight, OnProfileRoseLight),
        ProfileAccentColors(ProfileGreenLight, OnProfileGreenLight),
        ProfileAccentColors(ProfileOliveLight, OnProfileOliveLight),
    ),
)

private val DarkProfileAccents = ProfileAccentPalette(
    accents = listOf(
        ProfileAccentColors(ProfileBlueDark, OnProfileBlueDark),
        ProfileAccentColors(ProfilePurpleDark, OnProfilePurpleDark),
        ProfileAccentColors(ProfileOrangeDark, OnProfileOrangeDark),
        ProfileAccentColors(ProfileRoseDark, OnProfileRoseDark),
        ProfileAccentColors(ProfileGreenDark, OnProfileGreenDark),
        ProfileAccentColors(ProfileOliveDark, OnProfileOliveDark),
    ),
)

internal val LocalProfileAccentPalette = staticCompositionLocalOf { LightProfileAccents }

private val LightHomeTiles = HomeTilePalette(
    yellow = HomeTileColors(HomeTileYellowLight, OnHomeTileYellowLight),
    mint = HomeTileColors(HomeTileMintLight, OnHomeTileMintLight),
    sky = HomeTileColors(HomeTileSkyLight, OnHomeTileSkyLight),
    lavender = HomeTileColors(HomeTileLavenderLight, OnHomeTileLavenderLight),
    coral = HomeTileColors(HomeTileCoralLight, OnHomeTileCoralLight),
    peach = HomeTileColors(HomeTilePeachLight, OnHomeTilePeachLight),
    aqua = HomeTileColors(HomeTileAquaLight, OnHomeTileAquaLight),
)

private val DarkHomeTiles = HomeTilePalette(
    yellow = HomeTileColors(HomeTileYellowDark, OnHomeTileYellowDark),
    mint = HomeTileColors(HomeTileMintDark, OnHomeTileMintDark),
    sky = HomeTileColors(HomeTileSkyDark, OnHomeTileSkyDark),
    lavender = HomeTileColors(HomeTileLavenderDark, OnHomeTileLavenderDark),
    coral = HomeTileColors(HomeTileCoralDark, OnHomeTileCoralDark),
    peach = HomeTileColors(HomeTilePeachDark, OnHomeTilePeachDark),
    aqua = HomeTileColors(HomeTileAquaDark, OnHomeTileAquaDark),
)

internal val LocalHomeTilePalette = staticCompositionLocalOf { LightHomeTiles }

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = White,
    primaryContainer = Neutral100,
    onPrimaryContainer = NeutralText,
    secondary = NeutralSecondaryText,
    onSecondary = White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = Info,
    onTertiary = White,
    error = Danger,
    background = White,
    onBackground = NeutralText,
    surface = White,
    onSurface = NeutralText,
    surfaceVariant = Neutral50,
    onSurfaceVariant = NeutralSecondaryText,
    surfaceTint = White,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
    inverseSurface = NeutralText,
    inverseOnSurface = White,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = Neutral100,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = DarkElevatedSurface,
    onPrimaryContainer = DarkText,
    secondary = DarkSecondaryText,
    onSecondary = DarkBackground,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = InfoLight,
    onTertiary = InfoDark,
    error = DangerLight,
    onError = DangerDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkElevatedSurface,
    onSurfaceVariant = DarkSecondaryText,
    surfaceTint = DarkSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    inverseSurface = DarkText,
    inverseOnSurface = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkElevatedSurface,
    surfaceContainerHighest = DarkElevatedSurface,
)

@Composable
fun HealthVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalProfileAccentPalette provides if (darkTheme) DarkProfileAccents else LightProfileAccents,
        LocalHomeTilePalette provides if (darkTheme) DarkHomeTiles else LightHomeTiles,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
