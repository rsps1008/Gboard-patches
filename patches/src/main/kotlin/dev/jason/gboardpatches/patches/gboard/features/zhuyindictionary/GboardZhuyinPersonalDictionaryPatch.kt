package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/** Observe complete personal-dictionary snapshots; preserve native candidates and commit handling. */
internal val gboardZhuyinPersonalDictionaryBytecodePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        mutableClass("Lqci;").methods.single { it.name=="dU" && it.returnType=="V" }
            .patchPersonalDictionaryChanged()
        mutableClass("Lhbf;").patchZhuyinContactsListener()
        mutableClass("Lndd;").methods.single {
            it.name == "j" && it.parameterTypes == listOf("Landroid/database/Cursor;")
        }.patchContactsRebuildCheck()
    }
}

internal fun MutableMethod.patchPersonalDictionaryChanged() {
    check(definingClass=="Lqci;" && name=="dU")
    addRuntimeHook(RuntimeCallId.ZHUYIN_PERSONAL_DICTIONARY_CHANGED,true)
}

internal fun MutableClass.patchZhuyinContactsListener() {
    check(type == "Lhbf;")
    val finish=methods.single { it.name=="c" && it.parameterTypes.isEmpty() }
    finish.addRuntimeHook(RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_MERGE, false)
    finish.addRuntimeHook(RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_FINISHED, true)
    methods.single { it.name=="b" && it.parameterTypes.isEmpty() }
        .addRuntimeHook(RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_ABORTED, false)
}

private fun MutableMethod.addRuntimeHook(call: RuntimeCallId, atReturn: Boolean) {
    if(implementation!!.instructions.any { it.isMethodReference(call.encodedAbi) })return
    if(!atReturn) {addInstructions(0,RuntimeCallEmitter.invoke(call,"p0"));return}
    val exit=implementation!!.instructions.indices.single { implementation!!.instructions[it].opcode==Opcode.RETURN_VOID }
    replaceInstruction(exit,RuntimeCallEmitter.invoke(call,"p0"))
    addInstructions(exit+1,"return-void")
}

internal fun MutableMethod.patchContactsRebuildCheck() {
    check(definingClass=="Lndd;" && name=="j" && returnType=="Z")
    val call=RuntimeCallId.ZHUYIN_CONTACTS_DICTIONARY_REBUILD_REQUIRED
    if(implementation!!.instructions.any { it.isMethodReference(call.encodedAbi) })return
    addInstructionsWithLabels(0,"""
        ${RuntimeCallEmitter.invoke(call,"p0")}
        move-result v0
        if-eqz v0, :stock
        const/4 v0, 0x1
        return v0
    """.trimIndent(),ExternalLabel("stock",implementation!!.instructions.first()))
}
