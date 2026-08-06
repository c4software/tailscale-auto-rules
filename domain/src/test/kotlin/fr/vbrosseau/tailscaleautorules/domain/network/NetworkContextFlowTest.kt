package fr.vbrosseau.tailscaleautorules.domain.network

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Le debounce s'éprouve en temps virtuel : `runTest` avance l'horloge à la
 * demande, donc aucun de ces tests ne dure réellement plus de quelques
 * millisecondes.
 */
class NetworkContextFlowTest {
    private val window = 500.milliseconds

    private fun wifi(ssid: String) =
        NetworkContext(
            transport = NetworkTransport.WIFI,
            isInternetValidated = true,
            ssid = ssid,
        )

    @Test
    fun aBurstOfEventsCollapsesIntoItsLastValue() =
        runTest {
            // Séquence typique d'une association Wi-Fi : trois états intermédiaires
            // en quelques dizaines de millisecondes.
            val burst =
                flow {
                    emit(NetworkContext.Disconnected)
                    delay(50)
                    emit(NetworkContext(NetworkTransport.WIFI, ssid = "Maison"))
                    delay(50)
                    emit(wifi("Maison"))
                }

            assertEquals(listOf(wifi("Maison")), burst.stabilized(window).toList())
        }

    @Test
    fun valuesSeparatedByMoreThanTheWindowAreAllEmitted() =
        runTest {
            val slow =
                flow {
                    emit(wifi("Maison"))
                    delay(window.inWholeMilliseconds * 2)
                    emit(wifi("Bureau"))
                }

            assertEquals(
                listOf(wifi("Maison"), wifi("Bureau")),
                slow.stabilized(window).toList(),
            )
        }

    @Test
    fun anIdenticalContextIsNotEmittedTwice() =
        runTest {
            val repeated =
                flow {
                    emit(wifi("Maison"))
                    delay(window.inWholeMilliseconds * 2)
                    // Même contexte : rien ne justifie de réévaluer les règles.
                    emit(wifi("Maison"))
                    delay(window.inWholeMilliseconds * 2)
                    emit(wifi("Bureau"))
                }

            assertEquals(
                listOf(wifi("Maison"), wifi("Bureau")),
                repeated.stabilized(window).toList(),
            )
        }

    @Test
    fun aContextThatDiffersOnlyByAirplaneModeIsEmitted() =
        runTest {
            // La déduplication repose sur l'égalité structurelle : tout champ
            // significatif doit la rompre.
            val toggling =
                flow {
                    emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
                    delay(window.inWholeMilliseconds * 2)
                    emit(
                        NetworkContext(
                            NetworkTransport.CELLULAR,
                            isAirplaneModeOn = true,
                            isInternetValidated = true,
                        ),
                    )
                }

            assertEquals(2, toggling.stabilized(window).toList().size)
        }

    @Test
    fun aSingleValueStillReachesTheCollector() =
        runTest {
            assertEquals(
                listOf(NetworkContext.Disconnected),
                flowOf(NetworkContext.Disconnected).stabilized(window).toList(),
            )
        }

    @Test
    fun anEmptyFlowStaysEmpty() =
        runTest {
            assertEquals(
                emptyList(),
                flowOf<NetworkContext>().stabilized(window).toList(),
            )
        }

    @Test
    fun theDefaultWindowIsUsedWhenNoneIsGiven() =
        runTest {
            val burst =
                flow {
                    emit(NetworkContext.Disconnected)
                    delay(DefaultStabilizationWindow.inWholeMilliseconds / 2)
                    emit(wifi("Maison"))
                }

            assertEquals(listOf(wifi("Maison")), burst.stabilized().toList())
        }
}
