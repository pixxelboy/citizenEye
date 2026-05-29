package com.citizeneye.data

interface VoteDetailRepository {
    suspend fun getVoteDetail(vote: Vote, depute: Depute): VoteDetail
}

class DefaultVoteDetailRepository(
    private val externalResourcesRepository: ExternalResourcesRepository = FakeExternalResourcesRepository(),
    private val officialEnrichmentRepository: OfficialVoteEnrichmentRepository = EmptyOfficialVoteEnrichmentRepository
) : VoteDetailRepository {
    override suspend fun getVoteDetail(vote: Vote, depute: Depute): VoteDetail {
        val subjectType = classifyVoteSubjectType(vote.title)
        val parentText = officialEnrichmentRepository.findParentTextForVote(vote)
        val amendment = officialEnrichmentRepository.findAmendmentDetailsForVote(vote)
        val article = officialEnrichmentRepository.findArticleDetailsForVote(vote)
        val motion = officialEnrichmentRepository.findMotionDetailsForVote(vote)
        val officialSources = buildOfficialSources(vote, parentText, amendment, article, motion)
        val externalResources = externalResourcesRepository.getExternalResources(
            ExternalResourceQuery(
                voteTitle = vote.title,
                parentTextTitle = parentText?.title,
                date = vote.date,
                subjectType = subjectType,
                officialKeywords = buildOfficialKeywords(vote, parentText, amendment, subjectType)
            )
        )

        return VoteDetail(
            voteId = vote.id,
            voteNumber = vote.number,
            officialTitle = vote.title,
            plainLanguageTitle = null,
            date = vote.date,
            subjectType = subjectType,
            subjectExplanation = buildSubjectExplanation(subjectType),
            voteEffectExplanation = buildVoteEffectExplanation(subjectType, vote.deputePosition, vote.result),
            result = vote.result,
            deputyPosition = vote.deputePosition,
            sourceUrl = vote.sourceUrl.takeIf { it.isNotBlank() },
            parentText = parentText,
            amendment = amendment,
            article = article,
            motion = motion,
            voteBreakdown = vote.voteBreakdown,
            groupPosition = vote.groupPosition ?: depute.group.takeIf { it.isNotBlank() }?.let { groupName ->
                GroupVotePosition(
                    groupName = groupName,
                    groupMajorityPosition = null,
                    deputyVotedLikeGroup = null,
                    forCount = null,
                    againstCount = null,
                    abstentionCount = null,
                    nonVotingCount = null
                )
            },
            officialSources = officialSources,
            externalResources = externalResources
        )
    }

    private fun buildOfficialSources(
        vote: Vote,
        parentText: ParentTextDetails?,
        amendment: AmendmentDetails?,
        article: ArticleDetails?,
        motion: MotionDetails?
    ): List<OfficialSource> = buildList {
        vote.sourceUrl.takeIf { it.isNotBlank() }?.let {
            add(OfficialSource("Scrutin public n°${vote.number}", "Résultat et positions nominatives du scrutin.", it, OfficialSourceType.PUBLIC_VOTE))
        }
        parentText?.dossierUrl?.let { add(OfficialSource("Dossier législatif", parentText.title, it, OfficialSourceType.LEGISLATIVE_FILE)) }
        amendment?.sourceUrl?.let { add(OfficialSource("Amendement", amendment.number?.let { number -> "Amendement n° $number" }, it, OfficialSourceType.AMENDMENT)) }
        article?.sourceUrl?.let { add(OfficialSource("Article", article.number?.let { number -> "Article $number" }, it, OfficialSourceType.TEXT)) }
        motion?.sourceUrl?.let { add(OfficialSource("Motion", motion.type, it, OfficialSourceType.OTHER)) }
    }

    private fun buildOfficialKeywords(
        vote: Vote,
        parentText: ParentTextDetails?,
        amendment: AmendmentDetails?,
        subjectType: VoteSubjectType
    ): List<String> {
        val titleWords = vote.title
            .replace(Regex("[^A-Za-zÀ-ÖØ-öø-ÿ0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 }
            .take(8)
        return (listOf("Assemblée nationale", "scrutin", vote.number) +
            subjectType.badgeLabel() +
            titleWords +
            listOfNotNull(parentText?.title, amendment?.number?.let { "amendement $it" }))
            .distinct()
    }
}

interface ExternalResourcesRepository {
    suspend fun getExternalResources(query: ExternalResourceQuery): ExternalResourcesState
}

class FakeExternalResourcesRepository : ExternalResourcesRepository {
    override suspend fun getExternalResources(query: ExternalResourceQuery): ExternalResourcesState =
        ExternalResourcesState.Empty("Aucune source externe n’est configurée pour le moment.")
}
