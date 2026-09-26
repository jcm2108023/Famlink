package app.recess.android.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.components.Divider
import app.recess.android.ui.components.LabeledSlider
import app.recess.android.ui.components.RCard
import app.recess.android.ui.components.ScreenHeader
import app.recess.android.ui.components.ToggleRow
import app.recess.android.ui.theme.Palette
import app.recess.core.FamilyState
import app.recess.core.Format
import app.recess.core.RuleTrigger

@Composable
fun RulesScreen(state: FamilyState, vm: FamilyViewModel) {
    val settings = state.settings
    ScreenColumn {
        ScreenHeader(
            "Automation",
            subtitle = "Recess watches Classroom submissions and converts them into bonus minutes. Caps and approvals keep the evening from running away.",
        )
        RCard {
            LabeledSlider(
                "Daily bonus cap · per child, across every rule",
                Format.minutesLabel(settings.dailyBonusCapMin),
                settings.dailyBonusCapMin,
                15..120,
                5,
                { v -> vm.patchSettings { it.copy(dailyBonusCapMin = v) } },
            )
            Divider()
            ToggleRow(
                "Apply after bedtime",
                "Otherwise auto-apply waits for your approval.",
                settings.applyAfterBedtime,
                { on -> vm.patchSettings { it.copy(applyAfterBedtime = on) } },
            )
        }
        state.rules.forEach { rule ->
            RCard {
                ToggleRow(rule.title, rule.detail, rule.enabled, { on -> vm.patchRule(rule.id) { it.copy(enabled = on) } })
                LabeledSlider(
                    "Bonus", "+${Format.minutesLabel(rule.minutes)}", rule.minutes, 5..45, 5,
                    { v -> vm.patchRule(rule.id) { it.copy(minutes = v) } },
                    enabled = rule.enabled, valueColor = Palette.Accent,
                )
                if (rule.trigger == RuleTrigger.HighGrade) {
                    LabeledSlider(
                        "Threshold", "${rule.highGradeThreshold}%+", rule.highGradeThreshold, 70..100, 5,
                        { v -> vm.patchRule(rule.id) { it.copy(highGradeThreshold = v) } },
                        enabled = rule.enabled,
                    )
                }
            }
        }
        RCard {
            Text("Per child", style = MaterialTheme.typography.titleMedium)
            Text(
                "Auto-apply approves bonus minutes as soon as they’re earned. Off means you approve each grant.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
            )
            state.children.forEach { child ->
                Divider()
                ToggleRow(child.firstName, child.device, child.autoApply, { on -> vm.patchChild(child.id) { it.copy(autoApply = on) } })
            }
        }
    }
}
