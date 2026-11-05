package app.komori.data

import org.junit.Assert.*
import org.junit.Test

class ImageAnimationBudgetTest {
    @Test fun animationsAboveFourMegapixelsUseTheStaticDecoderPath() {
        assertTrue(ImageInfo(2048, 2048, "image/gif", true).canAnimate)
        assertFalse(ImageInfo(2049, 2048, "image/gif", true).canAnimate)
        assertFalse(ImageInfo(Int.MAX_VALUE, Int.MAX_VALUE, "image/gif", true).canAnimate)
        assertFalse(ImageInfo(32, 64, "image/png").canAnimate)
        assertTrue(ImageInfo(32, 64, "image/webp", true).canAnimate)
    }
}
