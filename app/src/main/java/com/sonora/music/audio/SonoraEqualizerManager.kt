package com.sonora.music.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SonoraEqualizerManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sonora_equalizer_settings", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    // Reactive StateFlows for Jetpack Compose UI
    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val bandLevels: StateFlow<Map<Int, Int>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _minBandLevel = MutableStateFlow(-1500)
    val minBandLevel: StateFlow<Int> = _minBandLevel.asStateFlow()

    private val _maxBandLevel = MutableStateFlow(1500)
    val maxBandLevel: StateFlow<Int> = _maxBandLevel.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(prefs.getInt(KEY_BASS_BOOST, 0))
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _equalizerSupported = MutableStateFlow(true)
    val equalizerSupported: StateFlow<Boolean> = _equalizerSupported.asStateFlow()

    private val _bassBoostSupported = MutableStateFlow(true)
    val bassBoostSupported: StateFlow<Boolean> = _bassBoostSupported.asStateFlow()

    private val _currentPreset = MutableStateFlow(prefs.getString(KEY_PRESET, "Flat") ?: "Flat")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val defaultPresets = listOf(
        "Flat",
        "Bass Boost",
        "Vocal Booster",
        "Electronic",
        "Rock",
        "Pop",
        "Hip-Hop",
        "Acoustic",
        "Custom"
    )

    private val _availablePresets = MutableStateFlow(defaultPresets)
    val availablePresets: StateFlow<List<String>> = _availablePresets.asStateFlow()

    companion object {
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_BASS_BOOST = "eq_bass_boost"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_BAND_PREFIX = "eq_band_"

        private val DEFAULT_FREQUENCIES = listOf(60, 230, 910, 3600, 14000)

        @Volatile
        private var instance: SonoraEqualizerManager? = null

        fun getInstance(context: Context): SonoraEqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: SonoraEqualizerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        // Pre-populate with saved levels and default frequencies so UI is never blank
        val savedLevels = mutableMapOf<Int, Int>()
        for (i in DEFAULT_FREQUENCIES.indices) {
            savedLevels[i] = prefs.getInt(KEY_BAND_PREFIX + i, 0)
        }
        _bandFrequencies.value = DEFAULT_FREQUENCIES
        _bandLevels.value = savedLevels

        // Probe global audio session (0) to discover actual device hardware bands
        try {
            val probeEq = Equalizer(0, 0)
            val numBands = probeEq.numberOfBands.toInt()
            if (numBands > 0) {
                val freqs = mutableListOf<Int>()
                val probedLevels = mutableMapOf<Int, Int>()
                for (b in 0 until numBands) {
                    freqs.add(probeEq.getCenterFreq(b.toShort()) / 1000)
                    probedLevels[b] = prefs.getInt(KEY_BAND_PREFIX + b, 0)
                }
                _bandFrequencies.value = freqs
                _bandLevels.value = probedLevels
                val range = probeEq.bandLevelRange
                if (range != null && range.size >= 2) {
                    _minBandLevel.value = range[0].toInt()
                    _maxBandLevel.value = range[1].toInt()
                }
            }
            probeEq.release()
        } catch (ignored: Exception) {
            _equalizerSupported.value = false
        }
    }

    /**
     * Attaches audio effects to ExoPlayer's active audio session ID.
     */
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        currentSessionId = audioSessionId

        releaseEffects()

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                val range = bandLevelRange
                if (range != null && range.size >= 2) {
                    _minBandLevel.value = range[0].toInt()
                    _maxBandLevel.value = range[1].toInt()
                }

                val numBands = numberOfBands.toInt()
                if (numBands > 0) {
                    val freqs = mutableListOf<Int>()
                    val savedLevels = mutableMapOf<Int, Int>()

                    for (band in 0 until numBands) {
                        val centerFreqHz = getCenterFreq(band.toShort()) / 1000
                        freqs.add(centerFreqHz)

                        val savedLevel = prefs.getInt(KEY_BAND_PREFIX + band, 0)
                        if (_isEnabled.value) {
                            try {
                                setBandLevel(band.toShort(), savedLevel.toShort())
                            } catch (ignored: Exception) {}
                        }
                        savedLevels[band] = savedLevel
                    }

                    _bandFrequencies.value = freqs
                    _bandLevels.value = savedLevels
                    _equalizerSupported.value = true
                } else {
                    _equalizerSupported.value = false
                }
                enabled = _isEnabled.value && _equalizerSupported.value
            }
        } catch (e: Exception) {
            e.printStackTrace()
            equalizer = null
            _equalizerSupported.value = false
        }

        // Bass Boost setup
        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                val supported = strengthSupported
                _bassBoostSupported.value = supported
                if (supported) {
                    val str = _bassBoostStrength.value
                    setStrength(str.toShort())
                    enabled = _isEnabled.value && str > 0
                } else {
                    enabled = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bassBoost = null
            _bassBoostSupported.value = false
        }

        // Only apply preset if user has equalizer enabled
        if (_isEnabled.value && _equalizerSupported.value) {
            val savedPresetName = _currentPreset.value
            if (savedPresetName != "Custom") {
                applyPreset(savedPresetName)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

        if (enabled && currentSessionId > 0 && equalizer == null) {
            attachAudioSession(currentSessionId)
            return
        }

        try {
            equalizer?.enabled = enabled && _equalizerSupported.value
            bassBoost?.enabled = enabled && _bassBoostSupported.value && _bassBoostStrength.value > 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBandLevel(band: Int, level: Int) {
        val clampedLevel = level.coerceIn(_minBandLevel.value, _maxBandLevel.value)
        val updated = _bandLevels.value.toMutableMap()
        updated[band] = clampedLevel
        _bandLevels.value = updated
        _currentPreset.value = "Custom"

        prefs.edit()
            .putInt(KEY_BAND_PREFIX + band, clampedLevel)
            .putString(KEY_PRESET, "Custom")
            .apply()

        try {
            equalizer?.setBandLevel(band.toShort(), clampedLevel.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        prefs.edit().putInt(KEY_BASS_BOOST, clamped).apply()

        try {
            bassBoost?.apply {
                if (strengthSupported) {
                    setStrength(clamped.toShort())
                    enabled = _isEnabled.value && clamped > 0
                } else {
                    enabled = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(presetName: String) {
        _currentPreset.value = presetName
        prefs.edit().putString(KEY_PRESET, presetName).apply()

        val numBands = _bandFrequencies.value.size
        if (numBands == 0) return

        val targetCurves = when (presetName) {
            "Bass Boost" -> listOf(0.70f, 0.45f, 0.15f, 0.0f, 0.0f)
            "Vocal Booster" -> listOf(-0.15f, 0.20f, 0.50f, 0.40f, 0.15f)
            "Electronic" -> listOf(0.55f, 0.35f, 0.0f, 0.30f, 0.50f)
            "Rock" -> listOf(0.50f, 0.25f, -0.10f, 0.30f, 0.45f)
            "Pop" -> listOf(-0.10f, 0.25f, 0.45f, 0.30f, -0.10f)
            "Hip-Hop" -> listOf(0.65f, 0.40f, 0.05f, 0.20f, 0.35f)
            "Acoustic" -> listOf(0.35f, 0.20f, 0.25f, 0.35f, 0.30f)
            else -> listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f) // Flat
        }

        val maxL = _maxBandLevel.value
        val minL = _minBandLevel.value
        val updated = mutableMapOf<Int, Int>()

        for (band in 0 until numBands) {
            val ratio = if (numBands > 1) band.toFloat() / (numBands - 1).toFloat() else 0.5f
            val curveIdx = (ratio * (targetCurves.size - 1)).toInt().coerceIn(0, targetCurves.size - 1)
            val normalizedVal = targetCurves[curveIdx]
            val level = (normalizedVal * if (normalizedVal >= 0) maxL else -minL).toInt()

            updated[band] = level
            prefs.edit().putInt(KEY_BAND_PREFIX + band, level).apply()

            try {
                equalizer?.setBandLevel(band.toShort(), level.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _bandLevels.value = updated

        if (presetName == "Bass Boost") {
            setBassBoost(650)
        }
    }

    fun detachAudioSession() {
        releaseEffects()
        currentSessionId = 0
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            equalizer = null
            bassBoost = null
        }
    }
}
