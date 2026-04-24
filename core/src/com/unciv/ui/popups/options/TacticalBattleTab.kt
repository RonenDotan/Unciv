package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.input.onChange

internal class TacticalBattleTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup) {

    private val dependentCheckboxes = mutableListOf<CheckBox>()

    override fun lateInitialize() {
        addHeader("Tactical Battles")

        val masterCheckbox = "Use Tactical Battles".toCheckBox(settings.useTacticalBattles) {
            settings.useTacticalBattles = it
            updateDependentState(it)
        }
        add(masterCheckbox).colspan(2).left().row()

        addDependentCheckbox("Tactical Battles for AI attacks", settings.useTacticalBattlesAI) {
            settings.useTacticalBattlesAI = it
        }

        addHeader("AI Tactics")

        addDependentCheckbox("Focus fire", settings.aiTacticFocusFire) { settings.aiTacticFocusFire = it }
        addDependentCheckbox("Low HP retreat", settings.aiTacticRetreat) { settings.aiTacticRetreat = it }
        addDependentCheckbox("Ranged units behind melee", settings.aiTacticRangedBehindMelee) { settings.aiTacticRangedBehindMelee = it }
        addDependentCheckbox("Target priority", settings.aiTacticTargetPriority) { settings.aiTacticTargetPriority = it }
        addDependentCheckbox("City defense", settings.aiTacticCityDefense) { settings.aiTacticCityDefense = it }

        updateDependentState(settings.useTacticalBattles)

        super.lateInitialize()
    }

    private fun addDependentCheckbox(text: String, initialState: Boolean, action: (Boolean) -> Unit) {
        val checkbox = text.toCheckBox(initialState) { action(it) }
        dependentCheckboxes.add(checkbox)
        add(checkbox).colspan(2).left().row()
    }

    private fun updateDependentState(enabled: Boolean) {
        val color = if (enabled) Color.WHITE else Color.GRAY
        for (cb in dependentCheckboxes) {
            cb.isDisabled = !enabled
            cb.color = color
        }
    }
}
