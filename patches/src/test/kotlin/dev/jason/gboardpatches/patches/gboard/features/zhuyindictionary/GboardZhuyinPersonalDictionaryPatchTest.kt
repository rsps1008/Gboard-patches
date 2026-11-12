package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

class GboardZhuyinPersonalDictionaryPatchTest {
    private fun originalClasses(): Map<String, com.android.tools.smali.dexlib2.iface.ClassDef> {
        val apk = System.getenv("GBOARD_DICTIONARY_VALIDATION_APK")
        assumeNotNull(apk)
        val container = DexFileFactory.loadDexContainer(File(apk!!), Opcodes.getDefault())
        return container.dexEntryNames.asSequence().flatMap {
            container.getEntry(it)!!.dexFile.classes.asSequence()
        }.associateBy { it.type }
    }

    @Test fun `hooks snapshot and actual contacts rebuild twice while preserving decoder`() {
        val classes = originalClasses()
        val database = MutableClass(classes.getValue("Lqci;"))
        val changed = database.methods.single {it.name=="dU"}
        val decoderBefore = serialized(classes.getValue(HMM_PROCESSOR))
        val contacts=MutableClass(classes.getValue("Lhbf;"))
        val manager=MutableClass(classes.getValue("Lndd;"))
        val cache=manager.methods.single { it.name=="j" && it.parameterTypes==listOf("Landroid/database/Cursor;") }
        repeat(2) {
            changed.patchPersonalDictionaryChanged()
            contacts.patchZhuyinContactsListener()
            cache.patchContactsRebuildCheck()
        }
        val contactFinish=contacts.methods.single { it.name=="c" }.implementation!!.instructions
        val mergeRef=RuntimeAbiCatalog.abi(RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_MERGE).reference
        val finishRef=RuntimeAbiCatalog.abi(RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_FINISHED).reference
        assertEquals(1,contactFinish.count { it.isMethodReference(mergeRef) })
        assertTrue(contactFinish.first().isMethodReference(mergeRef))
        assertTrue(contactFinish[contactFinish.size-2].isMethodReference(finishRef))
        assertEquals(1,cache.implementation!!.instructions.count {
            it.isMethodReference(RuntimeAbiCatalog.abi(RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_REBUILD_REQUIRED).reference)
        })
        val changedBody=changed.implementation!!.instructions
        val changedRef=RuntimeAbiCatalog.abi(RuntimeCallId.ZHUYIN_PERSONAL_DICTIONARY_CHANGED).reference
        assertEquals(1,changedBody.count {it.isMethodReference(changedRef)})
        val changedExit=changedBody.indices.single {changedBody[it].opcode==Opcode.RETURN_VOID}
        assertTrue(changedBody[changedExit-1].isMethodReference(changedRef))
        assertArrayEquals(decoderBefore,serialized(classes.getValue(HMM_PROCESSOR)))
        val output=Files.createTempFile("zhuyin-personal-dictionary", ".dex").toFile()
        try {
            DexPool.writeTo(output.path,ImmutableDexFile(Opcodes.getDefault(),listOf(database,contacts,manager)))
            assertEquals(setOf("Lqci;","Lhbf;","Lndd;"),DexFileFactory.loadDexFile(output,Opcodes.getDefault()).classes.map { it.type }.toSet())
        } finally { output.delete() }
    }

    private fun serialized(type: com.android.tools.smali.dexlib2.iface.ClassDef): ByteArray {
        val output=Files.createTempFile("zhuyin-stock-contract", ".dex").toFile()
        try {
            DexPool.writeTo(output.path,ImmutableDexFile(Opcodes.getDefault(),listOf(type)))
            return output.readBytes()
        } finally { output.delete() }
    }

    @Test fun `stock contacts insertion is word only and matches the new native bridge ABI`() {
        val classes=originalClasses()
        val contactInsert=classes.getValue("Lhbi;").methods.single {
            it.name=="c" && it.parameterTypes==listOf("Ljava/lang/String;","I")
        }
        val nativeRef="Lcom/google/android/apps/inputmethod/libs/hmm/MutableDictionaryAccessorImpl;" +
            "->nativeInsertOrUpdate(JLjava/lang/String;IZ)Z"
        assertEquals(1,contactInsert.implementation!!.instructions.count { it.isMethodReference(nativeRef) })
        val init = classes.getValue("Lkkb;").methods.single { it.name == "<clinit>" }.implementation!!.instructions.toList()
        val schemas = init.indexOfFirst { it.isFieldReference("Lkkb;->r:[Ljava/lang/String;") }
        val array = init[schemas - 2] as FiveRegisterInstruction
        // Dictionary kinds are one-based: contacts=2, user=3. Their accessor schemes share one register.
        assertEquals(array.registerD, array.registerE)
        val databaseChanged=classes.getValue("Lqci;").methods.single { it.name=="dU" }
        assertTrue(databaseChanged.implementation!!.instructions.any {
            it.isMethodReference("Lndi;->z()V")
        })
        assertTrue(classes.getValue("Lndd;").methods.any { it.name=="A" && it.parameterTypes.isEmpty() })
        val native=classes.getValue("Lcom/google/android/apps/inputmethod/libs/hmm/MutableDictionaryAccessorImpl;")
        assertTrue(native.methods.any { it.name=="nativeExportAllEntries" && it.parameterTypes==listOf("J") })
        assertTrue(classes.getValue("Lkkb;").methods.single { it.name=="i" }.implementation!!.instructions.any {
            it.isMethodReference("Lqci;->B(Landroid/content/Context;)Lqci;")
        })
    }
}
