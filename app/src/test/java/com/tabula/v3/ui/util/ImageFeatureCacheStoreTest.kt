package com.tabula.v3.ui.util

import com.tabula.v3.data.model.ImageFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImageFeatureCacheStoreTest {
    private fun key(version: Long = 1L, hdr: Boolean = true, motion: Boolean = false) =
        ImageFeatureCacheKey(imageId = 10L, contentVersion = version, enableHdr = hdr, enableMotion = motion)

    @Test
    fun trustedNegativeIsCached() {
        val store = ImageFeatureCacheStore
        store.clear()

        store.putIfReliable(key(), ImageFeatures(isHdr = false, motionPhotoInfo = null, definitive = true))

        assertNotNull(store.get(key()))
        store.clear()
    }

    @Test
    fun inconclusiveNegativeIsNeverCached() {
        val store = ImageFeatureCacheStore
        store.clear()

        store.putIfReliable(key(), ImageFeatures(isHdr = false, motionPhotoInfo = null, definitive = false))

        assertNull(store.get(key()))
        store.clear()
    }

    @Test
    fun positiveIsCachedEvenWhenFlagOmitted() {
        val store = ImageFeatureCacheStore
        store.clear()

        store.putIfReliable(key(), ImageFeatures(isHdr = true, motionPhotoInfo = null, definitive = false))

        assertNotNull(store.get(key()))
        store.clear()
    }

    @Test
    fun contentVersionChangeInvalidatesEntry() {
        val store = ImageFeatureCacheStore
        store.clear()

        store.putIfReliable(key(version = 1L), ImageFeatures(isHdr = true, motionPhotoInfo = null))
        assertNull(store.get(key(version = 2L)))
        assertNotNull(store.get(key(version = 1L)))
        store.clear()
    }

    @Test
    fun switchCombinationsAreIsolated() {
        val store = ImageFeatureCacheStore
        store.clear()

        store.putIfReliable(key(hdr = true), ImageFeatures(isHdr = true, motionPhotoInfo = null))

        assertNull(store.get(key(hdr = false)))
        assertNotNull(store.get(key(hdr = true)))
        store.clear()
    }

    @Test
    fun clearRemovesEverything() {
        val store = ImageFeatureCacheStore
        store.clear()
        store.putIfReliable(key(), ImageFeatures(isHdr = true, motionPhotoInfo = null))

        store.clear()

        assertNull(store.get(key()))
    }

    @Test
    fun positiveFeatureRoundTripsFields() {
        val store = ImageFeatureCacheStore
        store.clear()
        val features = ImageFeatures(isHdr = true, motionPhotoInfo = null)

        store.putIfReliable(key(), features)

        assertEquals(features, store.get(key()))
        store.clear()
    }
}
