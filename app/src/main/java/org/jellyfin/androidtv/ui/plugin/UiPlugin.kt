package org.jellyfin.androidtv.ui.plugin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.jellyfin.androidtv.ui.navigation.Destination
import org.jellyfin.androidtv.ui.navigation.NavigationRepository

interface UiPlugin {
    val id: String
    val name: String
    val version: String

    @Composable
    fun AppNavHost(
        navigationRepository: NavigationRepository,
        modifier: Modifier
    )

    fun createFragment(destination: Destination.Fragment): Fragment

    fun createViewModelFactory(): ViewModelProvider.Factory

    @Composable
    fun Theme(content: @Composable () -> Unit)

    fun getComponentFactory(): ComponentFactory

    interface ComponentFactory {
        @Composable
        fun PosterCard(
            item: Any,
            onClick: () -> Unit,
            modifier: Modifier
        )

        @Composable
        fun LandscapeCard(
            item: Any,
            onClick: () -> Unit,
            modifier: Modifier
        )

        @Composable
        fun SectionTitle(
            title: String,
            onSeeAll: (() -> Unit)?,
            modifier: Modifier
        )

        @Composable
        fun HeroSection(
            item: Any,
            onPlay: () -> Unit,
            onAddToFavorites: () -> Unit,
            modifier: Modifier
        )

        @Composable
        fun TopBar(
            currentDestination: Destination?,
            onNavigate: (Destination) -> Unit,
            modifier: Modifier
        )
    }
}

@Composable
fun RememberUiPlugin(): UiPlugin {
    return org.koin.compose.koinInject<UiPlugin>()
}