package net.ankio.ledger.ocr

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalModeSettingsTest {
    @Test fun upgradeRetiresRemoteSelectionAndCredentialsBeforeLocalRecognition() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val namespace="retired-remote-${UUID.randomUUID()}"
        val context=object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(if(name=="remote_ai")namespace else name,mode)
            override fun deleteSharedPreferences(name:String)=super.deleteSharedPreferences(if(name=="remote_ai")namespace else name)
        }
        try {
            context.getSharedPreferences("remote_ai",0).edit().putString("recognitionMode","remote")
                .putString("provider","legacy").putString("model","legacy-model")
                .putString("baseUrl","https://example.invalid").putString("keyCipher","obsolete-encrypted-key")
                .putBoolean("consent",true).commit()
            val settings=RecognitionSession(context)
            assertTrue(context.getSharedPreferences("remote_ai",0).all.isEmpty())
            val generation=settings.generation
            settings.invalidate()
            assertTrue(settings.generation>generation)
            RecognitionSession(context)
            assertTrue(context.getSharedPreferences("remote_ai",0).all.isEmpty())
        } finally { base.deleteSharedPreferences(namespace) }
    }
}
