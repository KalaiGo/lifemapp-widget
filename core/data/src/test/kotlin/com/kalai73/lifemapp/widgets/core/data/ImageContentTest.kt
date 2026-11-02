package com.kalai73.lifemapp.widgets.core.data

import com.kalai73.lifemapp.widgets.core.data.presentation.ImageContent
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ImageContentTest {
    @Test
    fun readsTheImgFormat() {
        assertEquals("https://x.supabase.co/storage/v1/object/public/images/u/1_a.jpg", ImageContent.uri(" [IMG:https://x.supabase.co/storage/v1/object/public/images/u/1_a.jpg] "))
    }

    @Test
    fun readsTheLegacyJsonFormat() {
        assertEquals("https://e.com/a.png", ImageContent.uri("""{"type":"image","uri":"https://e.com/a.png"}"""))
        assertNull(ImageContent.uri("""{"type":"text","uri":"https://e.com/a.png"}"""))
    }

    @Test
    fun otherContentIsNoImage() {
        assertNull(ImageContent.uri(null))
        assertNull(ImageContent.uri(""))
        assertNull(ImageContent.uri("[IMG:]"))
        assertNull(ImageContent.uri("<p>Hello</p>"))
    }

    @Test
    fun findsPicturesEmbeddedInText() {
        assertTrue(ImageContent.embedsImage("<p>a</p><IMG src=\"x\">"))
        assertFalse(ImageContent.embedsImage("<p>imgur</p>"))
        assertFalse(ImageContent.embedsImage(null))
    }
}
