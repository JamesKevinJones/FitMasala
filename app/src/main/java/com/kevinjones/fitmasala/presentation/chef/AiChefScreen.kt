package com.kevinjones.fitmasala.presentation.chef

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto

@Composable
fun AiChefScreen(
    contentPadding: PaddingValues,
    viewModel: ChefViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Fm.gutter),
            verticalArrangement = Arrangement.spacedBy(Fm.snug)
        ) {
            items(state.chatMessages) { message ->
                ChefMessageItem(message, onCookAndEat = viewModel::cookAndEat)
            }
            if (state.isAiLoading) {
                item {
                    Text(
                        "The chef is thinking...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fm.textSecondary,
                        modifier = Modifier.padding(Fm.snug)
                    )
                }
            }
        }

        FmCard(
            modifier = Modifier.fillMaxWidth().padding(Fm.gutter),
            contentPadding = PaddingValues(Fm.snug)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Fm.snug)
            ) {
                FmTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "What's in your dabba?"
                )
                FmButton(
                    text = "Ask",
                    onClick = {
                        viewModel.askChef(input)
                        input = ""
                    },
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}

@Composable
private fun ChefMessageItem(
    message: ChefMessage,
    onCookAndEat: (RecipeDto, String) -> Unit
) {
    when (message) {
        is ChefMessage.Text -> ChatBubble(message.text, message.isUser)
        is ChefMessage.Recipe -> RecipeCard(message.recipe, message.rawJson, onCookAndEat)
        is ChefMessage.Error -> ChatBubble(message.message, false)
    }
}

@Composable
private fun ChatBubble(text: String, isUser: Boolean) {
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        FmCard(
            container = containerColor,
            contentPadding = PaddingValues(Fm.snug)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeDto,
    rawJson: String,
    onCookAndEat: (RecipeDto, String) -> Unit
) {
    FmCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Fm.gutter),
        verticalArrangement = Arrangement.spacedBy(Fm.snug)
    ) {
        Text(recipe.title, style = MaterialTheme.typography.titleLarge)
        Text(recipe.provenanceNote, style = MaterialTheme.typography.bodyMedium)
        
        Row(horizontalArrangement = Arrangement.spacedBy(Fm.gutter)) {
            Column {
                Text("Macros per serving", style = MaterialTheme.typography.labelSmall)
                Text(
                    "${recipe.macrosPerServing.calories.toInt()} kcal · ${recipe.macrosPerServing.proteinG.toInt()}g protein",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        FmButton(
            "Cook & Eat",
            onClick = { onCookAndEat(recipe, rawJson) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
