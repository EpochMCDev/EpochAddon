package com.epochaddon.skills.gui

sealed interface SkillsScreen {
    data object Root : SkillsScreen

    data class Category(val id: String) : SkillsScreen

    data class Tree(val id: String) : SkillsScreen
}
