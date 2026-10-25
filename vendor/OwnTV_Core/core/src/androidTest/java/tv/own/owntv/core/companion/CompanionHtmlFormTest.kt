package tv.own.owntv.core.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionHtmlFormTest {
    @Test
    fun newM3uFormShowsTwelveHoursAndSendsOneRefreshValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val html = CompanionHtml.formPage(context, "123456")

        assertTrue(html.contains("<option value=\"HOURS_12\" selected>"))
        assertFalse(html.contains("<option value=\"OFF\" selected>"))
        assertFalse(html.contains("type=\"hidden\" name=\"autoRefresh\""))
        assertTrue(html.contains("type=\"hidden\" name=\"isDefault\" value=\"false\""))
        assertTrue(html.contains("name=\"isDefault\" value=\"true\" checked"))
    }
}
