package tachiyomi.presentation.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CustomIcons.MangaUpdates: ImageVector
    get() {
        if (_mangaUpdates != null) {
            return _mangaUpdates!!
        }
        _mangaUpdates = Builder(
            name = "Manga Updates", defaultWidth = 24.dp, defaultHeight = 24.0.dp,
            viewportWidth = 381.0f, viewportHeight = 373.94443f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = SolidColor(Color(0x00000000)),
                strokeLineWidth = 0.0f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = EvenOdd
            ) {
                moveToRelative(277.859f, 18.375f)
                curveToRelative(-0.265f, 0.43f, -1.169f, 0.781f, -2.007f, 0.781f)
                curveToRelative(-8.873f, 0.0f, -20.251f, 13.114f, -28.809f, 33.203f)
                curveToRelative(-0.782f, 1.834f, -3.634f, 11.632f, -4.208f, 14.454f)
                curveToRelative(-0.898f, 4.413f, -3.079f, 12.994f, -3.674f, 14.453f)
                curveToRelative(-0.35f, 0.859f, -0.81f, 2.441f, -1.022f, 3.515f)
                curveToRelative(-0.212f, 1.074f, -1.118f, 4.678f, -2.012f, 8.008f)
                curveToRelative(-4.201f, 15.635f, -4.92f, 18.346f, -5.543f, 20.899f)
                curveToRelative(-0.367f, 1.503f, -1.075f, 4.14f, -1.573f, 5.859f)
                curveToRelative(-0.499f, 1.719f, -1.088f, 4.271f, -1.311f, 5.671f)
                curveToRelative(-0.222f, 1.401f, -0.717f, 2.74f, -1.099f, 2.976f)
                curveToRelative(-0.382f, 0.236f, -0.695f, 1.443f, -0.695f, 2.681f)
                curveToRelative(0.0f, 1.238f, -0.351f, 2.469f, -0.781f, 2.734f)
                curveToRelative(-0.43f, 0.266f, -0.781f, 1.18f, -0.781f, 2.032f)
                curveToRelative(0.0f, 0.851f, -0.719f, 3.907f, -1.598f, 6.789f)
                curveToRelative(-1.812f, 5.944f, -2.667f, 9.024f, -5.47f, 19.695f)
                curveToRelative(-0.507f, 1.934f, -1.202f, 4.219f, -1.543f, 5.078f)
                curveToRelative(-0.34f, 0.86f, -1.055f, 3.32f, -1.588f, 5.469f)
                curveToRelative(-0.533f, 2.148f, -1.269f, 4.24f, -1.635f, 4.648f)
                curveToRelative(-0.367f, 0.409f, -0.666f, 1.38f, -0.666f, 2.159f)
                curveToRelative(0.0f, 0.78f, -0.318f, 2.028f, -0.705f, 2.774f)
                curveToRelative(-0.388f, 0.746f, -1.132f, 2.938f, -1.653f, 4.872f)
                curveToRelative(-1.053f, 3.903f, -2.166f, 7.398f, -3.111f, 9.766f)
                curveToRelative(-0.343f, 0.859f, -1.046f, 2.793f, -1.562f, 4.297f)
                curveToRelative(-0.991f, 2.885f, -1.159f, 3.335f, -3.802f, 10.171f)
                curveToRelative(-0.917f, 2.371f, -1.667f, 4.573f, -1.667f, 4.891f)
                curveToRelative(0.0f, 0.319f, -0.663f, 2.07f, -1.473f, 3.891f)
                curveToRelative(-0.81f, 1.822f, -2.291f, 5.422f, -3.291f, 8.0f)
                curveToRelative(-4.157f, 10.711f, -4.783f, 11.224f, -6.321f, 5.181f)
                curveToRelative(-0.397f, -1.561f, -1.08f, -4.068f, -1.518f, -5.572f)
                curveToRelative(-0.438f, -1.504f, -1.102f, -4.141f, -1.476f, -5.859f)
                curveToRelative(-0.374f, -1.719f, -1.07f, -4.707f, -1.546f, -6.641f)
                curveToRelative(-0.477f, -1.934f, -1.18f, -4.922f, -1.563f, -6.641f)
                curveToRelative(-0.382f, -1.718f, -1.068f, -4.707f, -1.524f, -6.64f)
                curveToRelative(-1.708f, -7.246f, -3.402f, -15.866f, -4.684f, -23.828f)
                curveToRelative(-0.415f, -2.578f, -1.144f, -6.973f, -1.621f, -9.766f)
                curveToRelative(-0.477f, -2.793f, -1.172f, -7.363f, -1.544f, -10.156f)
                curveToRelative(-0.373f, -2.793f, -1.076f, -8.067f, -1.563f, -11.719f)
                curveToRelative(-3.441f, -25.836f, -5.037f, -66.225f, -3.528f, -89.33f)
                curveToRelative(1.001f, -15.327f, -3.799f, -23.717f, -17.01f, -29.735f)
                curveToRelative(-7.191f, -3.276f, -25.321f, -5.034f, -29.279f, -2.839f)
                curveToRelative(-0.746f, 0.414f, -2.762f, 1.423f, -4.481f, 2.242f)
                curveToRelative(-6.504f, 3.1f, -7.995f, 4.103f, -12.201f, 8.21f)
                curveToRelative(-7.255f, 7.083f, -12.793f, 16.694f, -15.378f, 26.686f)
                curveToRelative(-1.884f, 7.287f, -2.43f, 9.622f, -3.289f, 14.063f)
                curveToRelative(-0.498f, 2.578f, -1.07f, 5.391f, -1.27f, 6.25f)
                curveToRelative(-0.201f, 0.859f, -0.726f, 3.848f, -1.168f, 6.641f)
                curveToRelative(-0.442f, 2.792f, -1.099f, 6.835f, -1.46f, 8.984f)
                curveToRelative(-0.361f, 2.148f, -1.035f, 6.191f, -1.497f, 8.984f)
                curveToRelative(-0.79f, 4.779f, -1.409f, 7.903f, -3.131f, 15.821f)
                curveToRelative(-0.686f, 3.153f, -1.484f, 6.134f, -3.293f, 12.304f)
                curveToRelative(-0.44f, 1.504f, -0.988f, 3.706f, -1.218f, 4.893f)
                curveToRelative(-0.229f, 1.187f, -0.727f, 2.35f, -1.107f, 2.585f)
                curveToRelative(-0.38f, 0.235f, -0.691f, 1.215f, -0.691f, 2.178f)
                curveToRelative(0.0f, 0.963f, -0.291f, 2.084f, -0.646f, 2.493f)
                curveToRelative(-0.355f, 0.408f, -1.098f, 2.324f, -1.651f, 4.258f)
                curveToRelative(-0.554f, 1.933f, -1.28f, 4.218f, -1.613f, 5.078f)
                curveToRelative(-0.334f, 0.859f, -1.152f, 3.32f, -1.819f, 5.468f)
                curveToRelative(-1.417f, 4.574f, -6.577f, 19.609f, -7.506f, 21.875f)
                curveToRelative(-0.352f, 0.86f, -1.78f, 5.254f, -3.171f, 9.766f)
                curveToRelative(-1.392f, 4.512f, -2.817f, 8.906f, -3.167f, 9.766f)
                curveToRelative(-0.35f, 0.859f, -1.004f, 2.793f, -1.452f, 4.297f)
                curveToRelative(-0.449f, 1.503f, -1.48f, 4.843f, -2.291f, 7.421f)
                curveToRelative(-0.811f, 2.579f, -1.935f, 6.27f, -2.497f, 8.204f)
                curveToRelative(-0.562f, 1.933f, -1.312f, 3.849f, -1.667f, 4.257f)
                curveToRelative(-0.355f, 0.409f, -0.645f, 1.71f, -0.645f, 2.893f)
                curveToRelative(0.0f, 1.183f, -0.315f, 2.959f, -0.7f, 3.946f)
                curveToRelative(-0.385f, 0.987f, -1.088f, 3.288f, -1.563f, 5.114f)
                curveToRelative(-1.991f, 7.667f, -2.638f, 10.065f, -3.213f, 11.915f)
                curveToRelative(-0.334f, 1.074f, -1.022f, 4.062f, -1.53f, 6.64f)
                curveToRelative(-0.508f, 2.578f, -1.221f, 5.918f, -1.586f, 7.422f)
                curveToRelative(-4.571f, 18.867f, -4.575f, 47.925f, -0.009f, 59.766f)
                curveToRelative(9.6f, 24.888f, 40.718f, 31.115f, 54.659f, 10.937f)
                curveToRelative(2.442f, -3.534f, 5.504f, -11.626f, 5.504f, -14.544f)
                curveToRelative(0.0f, -1.067f, 0.314f, -2.274f, 0.697f, -2.682f)
                curveToRelative(0.899f, -0.959f, 1.538f, -7.556f, 2.415f, -24.961f)
                curveToRelative(1.066f, -21.154f, 2.992f, -34.13f, 7.826f, -52.735f)
                curveToRelative(0.391f, -1.504f, 0.945f, -3.706f, 1.23f, -4.894f)
                curveToRelative(0.285f, -1.188f, 0.828f, -2.351f, 1.207f, -2.585f)
                curveToRelative(0.378f, -0.234f, 0.688f, -1.113f, 0.688f, -1.952f)
                curveToRelative(0.0f, -0.84f, 0.32f, -2.138f, 0.711f, -2.884f)
                curveToRelative(1.079f, -2.06f, 3.976f, -9.676f, 3.976f, -10.454f)
                curveToRelative(0.0f, -0.377f, 0.745f, -2.351f, 1.655f, -4.388f)
                curveToRelative(1.566f, -3.505f, 3.349f, -7.658f, 5.07f, -11.811f)
                curveToRelative(0.83f, -2.001f, 1.103f, -1.386f, 3.602f, 8.109f)
                curveToRelative(0.821f, 3.122f, 1.876f, 6.864f, 2.973f, 10.547f)
                curveToRelative(0.32f, 1.074f, 1.014f, 3.535f, 1.542f, 5.469f)
                curveToRelative(0.529f, 1.933f, 1.233f, 4.218f, 1.564f, 5.078f)
                curveToRelative(0.332f, 0.859f, 1.043f, 3.144f, 1.58f, 5.078f)
                curveToRelative(1.809f, 6.518f, 2.541f, 8.977f, 3.137f, 10.547f)
                curveToRelative(1.024f, 2.691f, 2.103f, 6.132f, 2.941f, 9.375f)
                curveToRelative(1.561f, 6.043f, 2.686f, 10.175f, 3.296f, 12.109f)
                curveToRelative(0.339f, 1.074f, 1.023f, 3.711f, 1.521f, 5.859f)
                curveToRelative(1.312f, 5.663f, 2.374f, 9.822f, 3.203f, 12.556f)
                curveToRelative(0.401f, 1.319f, 0.729f, 3.149f, 0.729f, 4.065f)
                curveToRelative(0.0f, 0.916f, 0.315f, 2.473f, 0.7f, 3.46f)
                curveToRelative(0.385f, 0.987f, 1.115f, 4.255f, 1.621f, 7.263f)
                curveToRelative(0.507f, 3.008f, 1.206f, 6.348f, 1.553f, 7.422f)
                curveToRelative(0.348f, 1.074f, 1.066f, 3.535f, 1.595f, 5.469f)
                curveToRelative(4.333f, 15.813f, 16.073f, 32.694f, 22.805f, 32.793f)
                curveToRelative(0.727f, 0.011f, 1.538f, 0.371f, 1.804f, 0.801f)
                curveToRelative(0.632f, 1.023f, 18.616f, 1.023f, 22.015f, 0.0f)
                curveToRelative(1.427f, -0.43f, 3.913f, -1.181f, 5.524f, -1.668f)
                curveToRelative(7.96f, -2.408f, 16.014f, -8.263f, 18.375f, -13.358f)
                curveToRelative(3.84f, -8.287f, 5.258f, -11.707f, 5.258f, -12.686f)
                curveToRelative(0.0f, -0.606f, 0.351f, -1.32f, 0.781f, -1.585f)
                curveToRelative(0.43f, -0.266f, 0.781f, -1.166f, 0.781f, -2.0f)
                curveToRelative(0.0f, -0.834f, 0.352f, -1.516f, 0.782f, -1.516f)
                curveToRelative(0.429f, 0.0f, 0.781f, -0.59f, 0.781f, -1.312f)
                curveToRelative(0.0f, -0.721f, 0.333f, -2.391f, 0.739f, -3.711f)
                curveToRelative(0.643f, -2.085f, 2.449f, -9.125f, 4.724f, -18.414f)
                curveToRelative(0.369f, -1.504f, 1.1f, -4.844f, 1.626f, -7.422f)
                curveToRelative(0.526f, -2.578f, 1.471f, -6.621f, 2.099f, -8.985f)
                curveToRelative(0.629f, -2.363f, 1.32f, -5.268f, 1.536f, -6.455f)
                curveToRelative(0.217f, -1.187f, 0.704f, -2.35f, 1.085f, -2.585f)
                curveToRelative(0.38f, -0.235f, 0.691f, -1.44f, 0.691f, -2.678f)
                curveToRelative(0.0f, -1.239f, 0.351f, -2.469f, 0.781f, -2.735f)
                curveToRelative(0.43f, -0.265f, 0.781f, -1.458f, 0.781f, -2.65f)
                curveToRelative(0.0f, -1.192f, 0.241f, -2.409f, 0.536f, -2.703f)
                curveToRelative(0.778f, -0.779f, 2.564f, -6.135f, 2.577f, -7.733f)
                curveToRelative(0.007f, -0.752f, 0.364f, -1.367f, 0.794f, -1.367f)
                curveToRelative(0.429f, 0.0f, 0.78f, -0.615f, 0.778f, -1.367f)
                curveToRelative(-0.002f, -1.078f, 4.865f, -14.091f, 10.155f, -27.149f)
                curveToRelative(0.348f, -0.859f, 1.398f, -3.671f, 2.334f, -6.25f)
                curveToRelative(0.936f, -2.578f, 2.025f, -5.298f, 2.42f, -6.044f)
                curveToRelative(0.395f, -0.746f, 0.719f, -1.893f, 0.719f, -2.549f)
                curveToRelative(0.0f, -0.657f, 0.321f, -1.804f, 0.714f, -2.55f)
                curveToRelative(0.598f, -1.136f, 3.544f, -10.424f, 6.378f, -20.107f)
                curveToRelative(0.377f, -1.289f, 1.397f, -4.476f, 2.266f, -7.083f)
                curveToRelative(0.868f, -2.607f, 1.579f, -5.331f, 1.579f, -6.055f)
                curveToRelative(0.0f, -2.522f, 1.402f, -1.292f, 1.841f, 1.615f)
                curveToRelative(0.243f, 1.611f, 0.779f, 4.863f, 1.191f, 7.227f)
                curveToRelative(2.794f, 16.016f, 3.671f, 25.795f, 4.797f, 53.515f)
                curveToRelative(1.991f, 49.001f, 2.344f, 54.414f, 4.617f, 70.703f)
                curveToRelative(3.078f, 22.06f, 9.152f, 34.215f, 19.581f, 39.185f)
                curveToRelative(7.133f, 3.4f, 22.653f, 2.187f, 28.707f, -2.243f)
                curveToRelative(19.432f, -14.221f, 22.04f, -46.372f, 9.553f, -117.801f)
                curveToRelative(-0.928f, -5.305f, -2.158f, -11.739f, -3.099f, -16.211f)
                curveToRelative(-0.43f, -2.041f, -1.095f, -5.205f, -1.478f, -7.031f)
                curveToRelative(-0.383f, -1.826f, -1.467f, -6.836f, -2.41f, -11.133f)
                curveToRelative(-2.246f, -10.242f, -3.806f, -18.367f, -5.549f, -28.906f)
                curveToRelative(-0.426f, -2.578f, -1.109f, -6.621f, -1.518f, -8.985f)
                curveToRelative(-4.499f, -26.011f, -6.585f, -64.411f, -5.088f, -93.612f)
                curveToRelative(0.44f, -8.575f, 0.365f, -12.222f, -0.265f, -12.852f)
                curveToRelative(-0.484f, -0.484f, -0.88f, -1.356f, -0.88f, -1.938f)
                curveToRelative(0.0f, -4.124f, -8.955f, -13.084f, -16.411f, -16.419f)
                curveToRelative(-3.292f, -1.472f, -4.933f, -1.938f, -14.208f, -4.037f)
                curveToRelative(-4.641f, -1.05f, -16.788f, -1.083f, -17.428f, -0.048f)
                moveTo(-28.0f, -16.0f)
                horizontalLineToRelative(20.0f)
                verticalLineToRelative(20.0f)
                horizontalLineToRelative(-20.0f)
                close()
                moveTo(402.0f, -16.0f)
                horizontalLineToRelative(20.0f)
                verticalLineToRelative(20.0f)
                horizontalLineToRelative(-20.0f)
                close()
            }
        }
            .build()
        return _mangaUpdates!!
    }

private var _mangaUpdates: ImageVector? = null
