package com.chaoqun.depthwhite.core

import com.chaoqun.depthwhite.ml.ModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSizeTest {
    @Test
    fun smallMatchesModelStoreAssetName() {
        assertEquals("depth_anything_v2_vits.onnx", ModelSize.SMALL.fileName)
        assertEquals("models/depth_anything_v2_vits.onnx", ModelSize.SMALL.assetPath)
        assertTrue(ModelSize.SMALL.bundledInApk)
        assertEquals("models", ModelStore.ASSET_DIR)
    }

    @Test
    fun largerModelsAreOnDemandDownloads() {
        assertEquals("depth_anything_v2_vitb.onnx", ModelSize.BASE.fileName)
        assertEquals("depth_anything_v2_vitl.onnx", ModelSize.LARGE.fileName)
        assertFalse(ModelSize.BASE.bundledInApk)
        assertFalse(ModelSize.LARGE.bundledInApk)
    }

    @Test
    fun minAcceptableBytesIsSixtyPercent() {
        assertEquals(
            (ModelSize.SMALL.approxBytes * 0.6).toLong(),
            ModelStore.minAcceptableBytes(ModelSize.SMALL),
        )
    }
}
