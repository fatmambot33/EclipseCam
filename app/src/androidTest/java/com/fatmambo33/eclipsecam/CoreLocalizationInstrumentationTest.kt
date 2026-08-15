package com.fatmambo33.eclipsecam

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreLocalizationInstrumentationTest {
    @Test
    fun frenchResourcesCoverCoreSafetyNavigationCaptureAndGallery() {
        val context = localizedContext(Locale.FRENCH)

        assertEquals("Caméra", context.getString(R.string.tab_camera))
        assertEquals("Galerie", context.getString(R.string.tab_gallery))
        assertEquals("Autoriser la caméra", context.getString(R.string.enable_camera))
        assertEquals("Autoriser la localisation", context.getString(R.string.enable_location))
        assertEquals("Capture de l’éclipse armée", context.getString(R.string.capture_notification_title))
        assertEquals("Vos sessions d’éclipse", context.getString(R.string.gallery_sessions_title))
        assertEquals("Retour aux sessions", context.getString(R.string.gallery_back_to_sessions))
        assertEquals("Timelapse local", context.getString(R.string.gallery_timelapse_title))
        assertEquals("Générer le timelapse", context.getString(R.string.gallery_timelapse_generate))
        assertEquals("Montage par phase", context.getString(R.string.gallery_montage_title))
        assertEquals("Générer le montage", context.getString(R.string.gallery_montage_generate))
        assertEquals(
            "Phase partielle • début",
            context.getString(R.string.gallery_montage_slot_partial_early),
        )
        assertEquals("Exporter et partager", context.getString(R.string.gallery_export_title))
        assertEquals("Supprimer", context.getString(R.string.gallery_export_location_remove))
        assertEquals("Choisir la destination d’export", context.getString(R.string.gallery_export_choose_destination))
        assertEquals("Partager un média EclipseCam", context.getString(R.string.gallery_export_share_chooser))
        assertFalse(context.getString(R.string.solar_safety_warning).contains("Never look"))
        assertFalse(context.getString(R.string.gallery_sessions_empty_body).contains("Photos and"))
        assertFalse(context.getString(R.string.gallery_timelapse_body).contains("Silent H.264"))
        assertFalse(context.getString(R.string.gallery_montage_body).contains("Choose which"))
        assertFalse(context.getString(R.string.gallery_export_body).contains("Nothing leaves"))
        assertFalse(context.getString(R.string.gallery_export_privacy_remove).contains("Privacy default"))
    }

    @Test
    fun countdownFormattingUsesTheActiveLocaleResources() {
        val english = localizedContext(Locale.ENGLISH)
        val french = localizedContext(Locale.FRENCH)

        assertEquals(
            "Reference countdown\n2d 3h 4m 5s",
            english.getString(R.string.reference_countdown_format, 2, 3, 4, 5),
        )
        assertEquals(
            "Compte à rebours de référence\n2 j 3 h 4 min 5 s",
            french.getString(R.string.reference_countdown_format, 2, 3, 4, 5),
        )
    }

    @Test
    fun galleryFormattingUsesTheActiveLocaleResources() {
        val english = localizedContext(Locale.ENGLISH)
        val french = localizedContext(Locale.FRENCH)

        assertEquals(
            "Captures: 3 • Generated outputs: 2",
            english.getString(R.string.gallery_session_counts_format, 3, 2),
        )
        assertEquals(
            "Captures : 3 • Médias générés : 2",
            french.getString(R.string.gallery_session_counts_format, 3, 2),
        )
        assertEquals("Rendering 42%", english.getString(R.string.gallery_timelapse_rendering_format, 42))
        assertEquals("Génération 42 %", french.getString(R.string.gallery_timelapse_rendering_format, 42))
        assertEquals("1.5 MB", english.getString(R.string.gallery_size_megabytes_format, 1.5))
        assertEquals("1,5 Mo", french.getString(R.string.gallery_size_megabytes_format, 1.5))
        assertEquals(
            "Complete • 4 selected • 1 missing",
            english.getString(R.string.gallery_montage_complete_format, 4, 1),
        )
        assertEquals(
            "Terminé • 4 sélectionnées • 1 manquantes",
            french.getString(R.string.gallery_montage_complete_format, 4, 1),
        )
        assertEquals(
            "Original capture • 123 bytes",
            english.getString(R.string.gallery_export_asset_detail, "Original capture", 123L),
        )
        assertEquals(
            "Capture d’origine • 123 octets",
            french.getString(R.string.gallery_export_asset_detail, "Capture d’origine", 123L),
        )
    }

    private fun localizedContext(locale: Locale) =
        InstrumentationRegistry.getInstrumentation().targetContext.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(locale)) },
        )
}
