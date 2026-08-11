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
    fun frenchResourcesCoverCoreSafetyNavigationCaptureAndGalleryMontage() {
        val context = localizedContext(Locale.FRENCH)

        assertEquals("Caméra", context.getString(R.string.tab_camera))
        assertEquals("Galerie", context.getString(R.string.tab_gallery))
        assertEquals("Autoriser la caméra", context.getString(R.string.enable_camera))
        assertEquals("Autoriser la localisation", context.getString(R.string.enable_location))
        assertEquals("Capture de l’éclipse armée", context.getString(R.string.capture_notification_title))
        assertEquals("Montage par phase", context.getString(R.string.gallery_montage_title))
        assertEquals("Générer le montage", context.getString(R.string.gallery_montage_generate))
        assertEquals(
            "Phase partielle • début",
            context.getString(R.string.gallery_montage_slot_partial_early),
        )
        assertFalse(context.getString(R.string.solar_safety_warning).contains("Never look"))
        assertFalse(context.getString(R.string.gallery_montage_body).contains("Choose which"))
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
    fun galleryMontageFormattingUsesTheActiveLocaleResources() {
        val english = localizedContext(Locale.ENGLISH)
        val french = localizedContext(Locale.FRENCH)

        assertEquals(
            "Complete • 4 selected • 1 missing",
            english.getString(R.string.gallery_montage_complete_format, 4, 1),
        )
        assertEquals(
            "Terminé • 4 sélectionnées • 1 manquantes",
            french.getString(R.string.gallery_montage_complete_format, 4, 1),
        )
    }

    private fun localizedContext(locale: Locale) =
        InstrumentationRegistry.getInstrumentation().targetContext.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(locale)) },
        )
}
