package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.goal_error_already_exist
import com.neoutils.finsight.resources.goal_error_empty_title
import com.neoutils.finsight.util.UiText

enum class GoalError(val message: String) {
    EMPTY_TITLE(message = "Goal title cannot be empty"),
    ALREADY_EXIST(message = "Goal title already exists"),
}

fun GoalError.toUiText() = when (this) {
    GoalError.EMPTY_TITLE -> UiText.Res(Res.string.goal_error_empty_title)
    GoalError.ALREADY_EXIST -> UiText.Res(Res.string.goal_error_already_exist)
}
