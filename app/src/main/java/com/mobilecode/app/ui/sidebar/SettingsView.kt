package com.mobilecode.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.runtime.PackageManager
import com.mobilecode.app.core.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    settingsRepository: SettingsRepository,
    packageManager: PackageManager,
    modifier: Modifier = Modifier
) {
    val settings by settingsRepository.settings.collectAsState()
    val packages by packageManager.packages.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.SidebarBackground)
            .padding(12.dp)
    ) {
        Text(
            text = "SETTINGS & ENVIRONMENT",
            color = VsCodeTheme.Foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSectionHeader("WORKSPACE & SANDBOX")
                SettingSwitchRow(
                    label = "Restore Last Workspace",
                    checked = settings.workspace.restoreLastWorkspaceOnStartup,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(workspace = settings.workspace.copy(restoreLastWorkspaceOnStartup = checked))
                            )
                        }
                    }
                )
                SettingSwitchRow(
                    label = "Isolated Sandbox Environment",
                    checked = settings.workspace.sandboxEnforced,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(workspace = settings.workspace.copy(sandboxEnforced = checked))
                            )
                        }
                    }
                )
                SettingItemRow(label = "New Window Shortcut", value = settings.workspace.newWindowKeybinding)

                HorizontalDivider(color = VsCodeTheme.BorderColor, modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("EDITOR FORMATTING & AUTO-CLOSING")
                SettingItemRow(label = "Font Family", value = settings.editor.fontFamily)
                SettingItemRow(label = "Font Size", value = "${settings.editor.fontSize}px")
                SettingItemRow(label = "Tab Size", value = "${settings.editor.tabSize} spaces")
                SettingItemRow(label = "Auto-Close Pairs", value = "() {} [] \"\" '' ``")

                SettingSwitchRow(
                    label = "Auto-Indentation",
                    checked = settings.editor.autoIndent,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(editor = settings.editor.copy(autoIndent = checked))
                            )
                        }
                    }
                )

                SettingSwitchRow(
                    label = "Auto-Closing Brackets & Quotes",
                    checked = settings.editor.autoClosingBrackets == "always",
                    onCheckedChange = { checked ->
                        val mode = if (checked) "always" else "never"
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(
                                    editor = settings.editor.copy(
                                        autoClosingBrackets = mode,
                                        autoClosingQuotes = mode
                                    )
                                )
                            )
                        }
                    }
                )

                SettingSwitchRow(
                    label = "Minimap Enabled",
                    checked = settings.editor.minimapEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(editor = settings.editor.copy(minimapEnabled = checked))
                            )
                        }
                    }
                )

                HorizontalDivider(color = VsCodeTheme.BorderColor, modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("INTEGRATED TERMINAL")
                SettingItemRow(label = "Font Family", value = settings.terminal.fontFamily)
                SettingItemRow(label = "Font Size", value = "${settings.terminal.fontSize}px")
                SettingItemRow(label = "Clipboard Copy/Paste", value = "Enabled (Ctrl+C / Ctrl+V)")
                SettingItemRow(label = "Sandbox Confinement", value = "Active")

                HorizontalDivider(color = VsCodeTheme.BorderColor, modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("DEVELOPMENT RUNTIMES (PKG)")
            }

            items(packages) { pkg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${pkg.name} (${pkg.version})",
                            color = VsCodeTheme.Foreground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = pkg.description,
                            color = VsCodeTheme.SecondaryForeground,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                if (pkg.isInstalled) {
                                    packageManager.uninstallPackage(pkg.name)
                                } else {
                                    packageManager.installPackage(pkg.name) {}
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pkg.isInstalled) VsCodeTheme.HoverBackground else VsCodeTheme.BadgeBackground
                        ),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (pkg.isInstalled) "Uninstall" else "Install",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = VsCodeTheme.SecondaryForeground,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SettingItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = VsCodeTheme.Foreground, fontSize = 12.sp)
        Text(text = value, color = VsCodeTheme.SecondaryForeground, fontSize = 12.sp)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = VsCodeTheme.Foreground, fontSize = 12.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VsCodeTheme.BadgeBackground,
                uncheckedThumbColor = VsCodeTheme.SecondaryForeground,
                uncheckedTrackColor = VsCodeTheme.BorderColor
            )
        )
    }
}
