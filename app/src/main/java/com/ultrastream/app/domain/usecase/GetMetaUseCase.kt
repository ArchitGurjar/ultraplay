package com.ultrastream.app.domain.usecase

import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.repository.MetaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetMetaUseCase @Inject constructor(
    private val metaRepository: MetaRepository
) {
    suspend operator fun invoke(id: String, type: String): Flow<MetaItem> {
        return metaRepository.getMetaFlow(id, type)
    }
}

