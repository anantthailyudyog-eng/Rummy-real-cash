package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.GameViewModel
import com.example.ui.lobby.LobbyScreen
import com.example.ui.table.GameTableScreen
import com.example.ui.theme.VelvetRummyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VelvetRummyTheme {
                val vm: GameViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.screen) {
                    requestedOrientation = when (uiState.screen) {
                        AppScreen.LOBBY -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        AppScreen.TABLE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }

                when (uiState.screen) {
                    AppScreen.LOBBY -> {
                        LobbyScreen(
                            uiState = uiState,
                            onToggleMute = vm::toggleMute,
                            onToggleVoice = vm::toggleVoice,
                            onToggleLanguage = vm::toggleLanguage,
                            onClaimDailyBonus = vm::claimDailyBonus,
                            onSelectMode = vm::selectMode,
                            onSelectSeats = vm::selectSeats,
                            onPlayNow = vm::playNowMultiplayer,
                            onCpuPractice = vm::startCpuPractice
                        )
                    }
                    AppScreen.TABLE -> {
                        GameTableScreen(
                            uiState = uiState,
                            onBackToLobby = vm::backToLobby,
                            onRetryConnect = vm::retryMultiplayerConnect,
                            onSwitchToCpu = vm::startCpuPractice,
                            onToggleMute = vm::toggleMute,
                            onToggleVoice = vm::toggleVoice,
                            onToggleLanguage = vm::toggleLanguage,
                            onDrawClosed = vm::drawClosed,
                            onDrawOpen = vm::drawOpen,
                            onDiscard = vm::discardSelectedOrCard,
                            onRequestDrop = vm::requestDrop,
                            onConfirmDrop = vm::confirmDrop,
                            onDismissDrop = vm::dismissDropConfirm,
                            onTapCard = vm::toggleCardSelection,
                            onTapGap = vm::moveSelectedTo,
                            onDragCardToGap = vm::moveSingleCardTo,
                            onSort = vm::sortHand,
                            onNewGroup = vm::createNewGroupFromSelected,
                            onMoveHere = {
                                vm.moveSelectedTo(uiState.localGroups.size, 0)
                            },
                            onUngroup = vm::ungroupAll,
                            onOpenFinishReview = vm::openFinishReview,
                            onCancelFinishReview = vm::cancelFinishReview,
                            onConfirmFinishReview = vm::confirmFinishDeclaration,
                            onNextOrPlayAgain = vm::nextDealOrPlayAgain,
                            onShowInfoToast = vm::showToast
                        )
                    }
                }
            }
        }
    }
}
