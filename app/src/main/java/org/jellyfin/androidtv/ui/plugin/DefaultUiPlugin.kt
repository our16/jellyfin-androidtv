package org.jellyfin.androidtv.ui.plugin

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.jellyfin.androidtv.ui.composable.compat.AppNavigationHost
import org.jellyfin.androidtv.ui.navigation.Destination
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.core.parameter.ParametersHolder

class DefaultUiPlugin : UiPlugin {
    override val id = "default"
    override val name = "Default Jellyfin UI"
    override val version = "1.0"

    @Composable
    override fun AppNavHost(
        navigationRepository: NavigationRepository,
        modifier: Modifier
    ) {
        AppNavigationHost(
            modifier = modifier,
            navigationRepository = navigationRepository
        )
    }

    override fun createFragment(destination: Destination.Fragment): Fragment {
        val fragmentClass = destination.fragment.java
        val app: Application = org.koin.java.KoinJavaComponent.get(Application::class.java)
        return Fragment.instantiate(
            app,
            fragmentClass.name,
            destination.arguments
        )
    }

    override fun createViewModelFactory(): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                throw UnsupportedOperationException("Default plugin uses system ViewModelFactory")
            }
        }
    }

    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        org.jellyfin.androidtv.ui.base.JellyfinTheme(content = content)
    }

    override fun getComponentFactory(): UiPlugin.ComponentFactory = DefaultComponentFactory()

    private class DefaultComponentFactory : UiPlugin.ComponentFactory {
        @Composable
        override fun PosterCard(
            item: Any,
            onClick: () -> Unit,
            modifier: Modifier
        ) {
            Box(
                modifier = modifier
                    .sizeIn(minWidth = 130.dp, minHeight = 190.dp)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.toString().take(20),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        @Composable
        override fun LandscapeCard(
            item: Any,
            onClick: () -> Unit,
            modifier: Modifier
        ) {
            PosterCard(item, onClick, modifier)
        }

        @Composable
        override fun SectionTitle(
            title: String,
            onSeeAll: (() -> Unit)?,
            modifier: Modifier
        ) {
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                onSeeAll?.let {
                    TextButton(onClick = it) {
                        Text("查看全部 →")
                    }
                }
            }
        }

        @Composable
        override fun HeroSection(
            item: Any,
            onPlay: () -> Unit,
            onAddToFavorites: () -> Unit,
            modifier: Modifier
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Hero: ${item.toString()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        override fun TopBar(
            currentDestination: Destination?,
            onNavigate: (Destination) -> Unit,
            modifier: Modifier
        ) {
            TopAppBar(
                title = { Text("Jellyfin") },
                modifier = modifier
            )
        }
    }
}
