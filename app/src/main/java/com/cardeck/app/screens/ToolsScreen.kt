package com.cardeck.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.R
import com.cardeck.app.defaultFavorite
import com.cardeck.app.launch
import com.cardeck.app.rememberTools
import com.cardeck.app.title
import com.cardeck.app.ui.Accent
import com.cardeck.app.ui.Domain
import com.cardeck.app.ui.ScreenHeader
import com.cardeck.app.ui.ToolTile
import com.cardeck.app.ui.cs
import com.cardeck.app.ui.fadeUp
import com.cardeck.app.ui.rise

/** Tous les outils d'un domaine : groupes titrés, grille de tuiles à 2 colonnes. */
@Composable
fun ToolsScreen(vm: AppViewModel, d: Domain) {
    val res = LocalResources.current
    val groups = rememberTools(vm, d)
    val fav = vm.favorite(d) ?: d.defaultFavorite
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.tools_title), stringResource(d.title))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.tools_hint), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).fadeUp(),
            )
            var index = 0
            groups.forEach { g ->
                Text(
                    stringResource(g.title), style = MaterialTheme.typography.labelLarge, color = Accent.current.accent,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 10.dp).fadeUp(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    g.tools.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { t ->
                                ToolTile(
                                    t.sym, stringResource(t.name), Modifier.weight(1f).rise(index++),
                                    subtitle = t.reason?.let { stringResource(it) },
                                    enabled = t.enabled,
                                    favorite = t.id == fav,
                                    onClick = { vm.launch(t) },
                                    onLongClick = {
                                        vm.setFavorite(d, t.id)
                                        vm.showSnack(res.getString(R.string.tools_favorite_set, res.getString(t.name)))
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
