package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardZhuyinClassicCandidatesMarkerPatch = resourcePatch {
    compatibleWith(COMPATIBILITY_GBOARD)
    finalize {
        applyFeatureMarker("dev.jason.gboardpatches.feature.zhuyin_classic_candidates")
    }
}
