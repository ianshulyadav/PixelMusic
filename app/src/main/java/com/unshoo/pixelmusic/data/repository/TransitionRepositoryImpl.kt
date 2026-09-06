package com.unshoo.pixelmusic.data.repository

import com.unshoo.pixelmusic.data.database.TransitionDao
import com.unshoo.pixelmusic.data.database.TransitionRuleEntity
import com.unshoo.pixelmusic.data.model.TransitionResolution
import com.unshoo.pixelmusic.data.model.TransitionRule
import com.unshoo.pixelmusic.data.model.TransitionSettings
import com.unshoo.pixelmusic.data.model.TransitionSource
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionRepositoryImpl @Inject constructor(
    private val transitionDao: TransitionDao,
    private val userPreferences: UserPreferencesRepository
) : TransitionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun resolveTransitionSettings(
        playlistId: String,
        fromTrackId: String,
        toTrackId: String
    ): Flow<TransitionResolution> {
        return transitionDao.getSpecificRule(playlistId, fromTrackId, toTrackId)
            .flatMapLatest { specificRule ->
                if (specificRule != null) {
                    flowOf(
                        TransitionResolution(
                            settings = specificRule.settings,
                            source = TransitionSource.PLAYLIST_SPECIFIC,
                        )
                    )
                } else {
                    transitionDao.getPlaylistDefaultRule(playlistId).flatMapLatest { playlistRule ->
                        if (playlistRule != null) {
                            flowOf(
                                TransitionResolution(
                                    settings = playlistRule.settings,
                                    source = TransitionSource.PLAYLIST_DEFAULT,
                                )
                            )
                        } else {
                            userPreferences.globalTransitionSettingsFlow.map { settings ->
                                TransitionResolution(
                                    settings = settings,
                                    source = TransitionSource.GLOBAL_DEFAULT,
                                )
                            }
                        }
                    }
                }
            }
    }

    override fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRule>> {
        return transitionDao.getAllRulesForPlaylist(playlistId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRule?> {
        return transitionDao.getPlaylistDefaultRule(playlistId).map { entity ->
            entity?.toModel()
        }
    }

    override suspend fun saveRule(rule: TransitionRule) {
        transitionDao.setRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: Long) {
        transitionDao.deleteRule(ruleId)
    }

    override suspend fun deletePlaylistDefaultRule(playlistId: String) {
        transitionDao.deletePlaylistDefaultRule(playlistId)
    }

    override fun getGlobalSettings(): Flow<TransitionSettings> {
        return userPreferences.globalTransitionSettingsFlow
    }

    override suspend fun saveGlobalSettings(settings: TransitionSettings) {
        userPreferences.saveGlobalTransitionSettings(settings)
    }

    private fun TransitionRuleEntity.toModel(): TransitionRule {
        return TransitionRule(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }

    private fun TransitionRule.toEntity(): TransitionRuleEntity {
        return TransitionRuleEntity(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }
}
