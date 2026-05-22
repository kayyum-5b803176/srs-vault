package com.srspassword.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.data.ReviewType
import com.srspassword.app.viewmodel.PasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCardScreen(
    cardId : String?,
    onSaved: () -> Unit,
    onBack : () -> Unit,
    vm     : PasswordViewModel = hiltViewModel()
) {
    val isEditing    = cardId != null
    val selectedCard by vm.selectedCard.collectAsState()

    LaunchedEffect(cardId) { if (cardId != null) vm.loadCard(cardId) }

    var title        by remember { mutableStateOf("") }
    var username     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var hint         by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf("General") }
    var tags         by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var reviewType   by remember { mutableStateOf(ReviewType.VISUAL) }
    var errors       by remember { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(selectedCard) {
        selectedCard?.let { card ->
            title      = card.title
            username   = card.username
            hint       = card.hint
            category   = card.category
            tags       = card.tags
            reviewType = card.reviewType
        }
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (title.isBlank())    e["title"]    = "Title is required"
        if (username.isBlank()) e["username"] = "Username/email is required"
        if (!isEditing && password.isBlank()) e["password"] = "Password is required"
        errors = e
        return e.isEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Password" else "Add Password") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title *") },
                placeholder = { Text("e.g. Gmail Account") },
                isError = errors.containsKey("title"),
                supportingText = { errors["title"]?.let { Text(it) } },
                leadingIcon = { Icon(Icons.Default.Label, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username / Email *") },
                placeholder = { Text("e.g. user@gmail.com") },
                isError = errors.containsKey("username"),
                supportingText = { errors["username"]?.let { Text(it) } },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(if (isEditing) "New Password (leave blank to keep)" else "Password *") },
                isError = errors.containsKey("password"),
                supportingText = { errors["password"]?.let { Text(it) } },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = hint, onValueChange = { hint = it },
                label = { Text("Memory Hint (optional)") },
                placeholder = { Text("e.g. Starts with capital, ends with number") },
                leadingIcon = { Icon(Icons.Default.Lightbulb, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("Category") },
                placeholder = { Text("e.g. Social, Work, Banking") },
                leadingIcon = { Icon(Icons.Default.Category, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = tags, onValueChange = { tags = it },
                label = { Text("Tags (comma-separated)") },
                placeholder = { Text("e.g. personal, important") },
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Review Type Picker ─────────────────────────────────────────────
            Text(
                "Review Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReviewTypeCard(
                    title       = "Visual",
                    description = "Flip card — see and rate from memory",
                    icon        = Icons.Default.FlipToFront,
                    selected    = reviewType == ReviewType.VISUAL,
                    onClick     = { reviewType = ReviewType.VISUAL },
                    modifier    = Modifier.weight(1f)
                )
                ReviewTypeCard(
                    title       = "Input",
                    description = "Type the password — see exact mismatches",
                    icon        = Icons.Default.Keyboard,
                    selected    = reviewType == ReviewType.INPUT,
                    onClick     = { reviewType = ReviewType.INPUT },
                    modifier    = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (validate()) {
                        if (isEditing && selectedCard != null) {
                            vm.updateCard(
                                selectedCard!!.copy(
                                    title = title, username = username,
                                    hint = hint, category = category,
                                    tags = tags, reviewType = reviewType
                                ),
                                newPassword = password.ifBlank { null }
                            )
                        } else {
                            vm.addCard(title, username, password, hint, category, tags, reviewType)
                        }
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(if (isEditing) Icons.Default.Save else Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEditing) "Save Changes" else "Add Password Card")
            }
        }
    }
}

@Composable
private fun ReviewTypeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline
    val bgColor     = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surface

    Card(
        onClick = onClick,
        shape   = RoundedCornerShape(16.dp),
        colors  = CardDefaults.cardColors(containerColor = bgColor),
        border  = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = if (selected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style    = MaterialTheme.typography.labelSmall,
                color    = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f)
                           else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
