package com.indiewalkabout.nowdothis.feature.naturallanguage.di

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.AttributeParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.ReminderParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TemporalParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase.ParseNaturalLanguageTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NaturalLanguageModule {
    @Provides
    fun provideTemporalParser(): TemporalParser = TemporalParser()

    @Provides
    fun provideAttributeParser(): AttributeParser = AttributeParser()

    @Provides
    fun provideReminderParser(): ReminderParser = ReminderParser()

    @Provides
    fun provideParseNaturalLanguageTask(
        temporalParser: TemporalParser,
        attributeParser: AttributeParser,
        reminderParser: ReminderParser
    ): ParseNaturalLanguageTask = ParseNaturalLanguageTask(
        temporalParser = temporalParser,
        attributeParser = attributeParser,
        reminderParser = reminderParser
    )
}
