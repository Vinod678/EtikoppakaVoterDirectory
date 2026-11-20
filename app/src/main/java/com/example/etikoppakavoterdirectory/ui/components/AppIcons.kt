package com.example.etikoppakavoterdirectory.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons not included in material-icons-core. (Icons.Outlined.Star in the core set is drawn filled,
 * which made "not favourite" look almost like "favourite".)
 */
object AppIcons {
    /** Material "star_border": hollow star for the not-favourite state. */
    val StarBorder: ImageVector by lazy {
        materialIcon(name = "AppIcons.StarBorder") {
            materialPath {
                moveTo(22.0f, 9.24f)
                lineToRelative(-7.19f, -0.62f)
                lineTo(12.0f, 2.0f)
                lineTo(9.19f, 8.63f)
                lineTo(2.0f, 9.24f)
                lineToRelative(5.46f, 4.73f)
                lineTo(5.82f, 21.0f)
                lineTo(12.0f, 17.27f)
                lineTo(18.18f, 21.0f)
                lineToRelative(-1.63f, -7.03f)
                lineTo(22.0f, 9.24f)
                close()
                moveTo(12.0f, 15.4f)
                lineToRelative(-3.76f, 2.27f)
                lineToRelative(1.0f, -4.28f)
                lineToRelative(-3.32f, -2.88f)
                lineToRelative(4.38f, -0.38f)
                lineTo(12.0f, 6.1f)
                lineToRelative(1.71f, 4.04f)
                lineToRelative(4.38f, 0.38f)
                lineToRelative(-3.32f, 2.88f)
                lineToRelative(1.0f, 4.28f)
                lineTo(12.0f, 15.4f)
                close()
            }
        }
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(androidx.compose.ui.graphics.Color.Black))
            .build()

    /** Material "visibility" / "visibility_off" (show / hide password). */
    val Visibility: ImageVector by lazy {
        icon("AppIcons.Visibility", "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z")
    }
    val VisibilityOff: ImageVector by lazy {
        icon("AppIcons.VisibilityOff", "M12 7c2.76 0 5 2.24 5 5 0 0.65-0.13 1.26-0.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74 0.25-3.98 0.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28 0.46 0.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-0.3 4.38-0.84l0.42 0.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-0.05 0.21-0.08 0.43-0.08 0.65 0 1.66 1.34 3 3 3 0.22 0 0.44-0.03 0.65-0.08l1.55 1.55c-0.67 0.33-1.41 0.53-2.2 0.53-2.76 0-5-2.24-5-5 0-0.79 0.2-1.53 0.53-2.2zm4.31-0.78l3.15 3.15 0.02-0.16c0-1.66-1.34-3-3-3l-0.17 0.01z")
    }
    /** Material "logout". */
    val Logout: ImageVector by lazy {
        icon("AppIcons.Logout", "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 0.9-2 2v14c0 1.1 0.9 2 2 2h8v-2H4V5z")
    }
    /** Material "sort". */
    val Sort: ImageVector by lazy {
        icon("AppIcons.Sort", "M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z")
    }
    /** Material "image". */
    val Image: ImageVector by lazy {
        icon("AppIcons.Image", "M21 19V5c0-1.1-0.9-2-2-2H5c-1.1 0-2 0.9-2 2v14c0 1.1 0.9 2 2 2h14c1.1 0 2-0.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z")
    }
    /** Material "group" (total voters). */
    val Group: ImageVector by lazy {
        icon("AppIcons.Group", "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-0.29 0-0.62 0.02-0.97 0.05 1.16 0.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z")
    }
    /** Material "layers" (blocks filter). */
    val Layers: ImageVector by lazy {
        icon("AppIcons.Layers", "M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74zM12 16l7.36-5.73L21 9l-9-7-9 7 1.63 1.27L12 16z")
    }
    /** Material "grid_view" (ward overview). */
    val Grid: ImageVector by lazy {
        icon("AppIcons.Grid", "M3 3v8h8V3H3zm6 6H5V5h4v4zm-6 4v8h8v-8H3zm6 6H5v-4h4v4zm4-16v8h8V3h-8zm6 6h-4V5h4v4zm-6 4v8h8v-8h-8zm6 6h-4v-4h4v4z")
    }
}
