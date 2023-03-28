package sh.eliza.monocleimage

import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonocleImageTest {
  @Test
  fun fromRgb_blackAndWhiteHasNoUv() {
    val image = createMonocleImageFromResource("blackandwhite.png")

    assertEquals(0, image.uvRows.size)
    assertTrue(0 < image.yRows.size)
  }

  private fun createMonocleImageFromResource(path: String): MonocleImage {
    val image = ImageIO.read(this::class.java.classLoader.getResource(path))
    return MonocleImage.createFromRgb { row, col -> image.getRGB(col, row) }
  }
}
