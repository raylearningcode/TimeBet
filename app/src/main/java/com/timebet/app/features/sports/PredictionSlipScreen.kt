package com.timebet.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.SportsPredictionEntity
import com.timebet.app.design.theme.*
import com.timebet.app.features.casino.coinflip.StakeSelector
import com.timebet.app.util.TimeFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun PredictionSlipScreen(
    eventId: String,
    marketType: String,
    selection: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableLongStateOf(0L) }
    var stakeSeconds by remember { mutableLongStateOf(5 * 60L) }
    var maxActiveStake by remember { mutableLongStateOf(0L) }
    var currentActiveStake by remember { mutableLongStateOf(0L) }
    var isSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val odds = 2.10 // In production, fetched from provider
    val potentialProfit = (stakeSeconds * (odds - 1.0)).toLong()
    val theoreticalReturn = (stakeSeconds * odds).toLong()

    LaunchedEffect(Unit) {
        balance = ServiceLocator.timeBankEngine.getBalance()
        currentActiveStake = ServiceLocator.timeBankRepository.getActiveSportsStake()
        val bank = ServiceLocator.timeBankEngine.getDailyBank()
        maxActiveStake = (bank.baseAllowanceSeconds * 0.20).toLong()
    }

    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun place() {
        scope.launch {
            errorMessage = null

            // Validations
            if (stakeSeconds <= 0) {
                errorMessage = "Stake must be positive"
                return@launch
            }
            if (stakeSeconds > balance) {
                errorMessage = "Stake exceeds available balance"
                return@launch
            }
            if (currentActiveStake + stakeSeconds > maxActiveStake) {
                errorMessage = "Would exceed maximum active sports stake (${TimeFormatter.formatMinutesShort(maxActiveStake)})"
                return@launch
            }

            val prediction = SportsPredictionEntity(
                providerEventId = eventId,
                competition = "Premier League",
                homeTeam = "Arsenal",
                awayTeam = "Chelsea",
                marketType = marketType,
                selection = selection,
                oddsAtPlacement = odds,
                stakeSeconds = stakeSeconds,
                potentialProfitSeconds = potentialProfit,
                placedAt = System.currentTimeMillis(),
                placementLocalDate = today,
                status = "pending_cancelable"
            )

            ServiceLocator.timeBankRepository.placeSportsPrediction(prediction)
            isSuccess = true
            balance = ServiceLocator.timeBankEngine.getBalance()
        }
    }

    if (isSuccess) {
        // Success state
        Column(
            modifier = Modifier.fillMaxSize().background(TimeBetBlack),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Prediction Placed!", style = TimeBetTypography.headlineLarge, color = TimeBetGreen)
            Spacer(modifier = Modifier.height(16.dp))
            Text("$selection @ ${String.format("%.2f", odds)}", style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
            Text(
                "Stake: ${TimeFormatter.formatMinutesShort(stakeSeconds)}",
                style = TimeBetTypography.bodyMedium,
                color = TimeBetTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You can cancel this prediction until midnight tonight.",
                style = TimeBetTypography.labelSmall,
                color = TimeBetTextTertiary
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onBack,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(TimeBetBorderLight)
                ),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Done", color = TimeBetWhite) }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TimeBetBlack)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
                }
                Text("Prediction Slip", style = TimeBetTypography.labelLarge, color = TimeBetTextSecondary)
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selection summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Arsenal vs Chelsea", style = TimeBetTypography.bodyLarge, color = TimeBetWhite)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(selection, style = TimeBetTypography.labelLarge, color = TimeBetWhite)
                            Text(
                                String.format("%.2f", odds),
                                style = TimeBetTypography.headlineMedium,
                                color = TimeBetWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stake selector
                StakeSelector(
                    balance = balance.coerceAtMost(maxActiveStake - currentActiveStake + stakeSeconds),
                    stake = stakeSeconds,
                    onStakeChange = { stakeSeconds = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Payout summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimeBetSurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        SummaryRow("Stake", TimeFormatter.formatMinutesShort(stakeSeconds))
                        SummaryRow("Odds", String.format("%.2f", odds))
                        SummaryRow("Potential Return", TimeFormatter.formatMinutesShort(theoreticalReturn))
                        SummaryRow("Credited Profit", "+${TimeFormatter.formatMinutesShort(potentialProfit)}")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Stake is deducted immediately. Cancel today for full refund. After midnight, prediction is locked.",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )

                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let {
                    Text(it, style = TimeBetTypography.labelSmall, color = TimeBetRed)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { place() },
                    enabled = stakeSeconds > 0 && stakeSeconds <= balance,
                    colors = ButtonDefaults.buttonColors(containerColor = TimeBetWhite, contentColor = TimeBetBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("PLACE PREDICTION", style = TimeBetTypography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = TimeBetTypography.bodyMedium, color = TimeBetTextSecondary)
        Text(value, style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
    }
}
