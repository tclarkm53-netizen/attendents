package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testHtmlErrorSanitizerFormat() {
    val rawHtml = "<br /><b>Parse error</b>:  syntax error, unexpected identifier &quot;DB_PASS&quot; in <b>/home/verify-app/www/st/server/db.php</b> on line <b>23</b><br />"
    val unescaped = rawHtml
      .replace("&quot;", "\"")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&#039;", "'")

    assertTrue(unescaped.contains("Parse error"))
    val clean = unescaped.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    assertTrue(clean.contains("DB_PASS"))

    val escapedMsg = clean.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
    val syntheticJson = "{\"success\":false,\"message\":\"$escapedMsg\",\"timestamp\":${System.currentTimeMillis()}}"

    assertTrue(syntheticJson.startsWith("{\"success\":false"))
    assertTrue(syntheticJson.contains("DB_PASS"))
  }
}
