package com.kevinjones.fitmasala.di

import com.kevinjones.fitmasala.data.repository.MealRepositoryImpl
import com.kevinjones.fitmasala.data.repository.PlanRepositoryImpl
import com.kevinjones.fitmasala.data.repository.ProgressRepositoryImpl
import com.kevinjones.fitmasala.domain.repository.MealRepository
import com.kevinjones.fitmasala.domain.repository.PlanRepository
import com.kevinjones.fitmasala.domain.repository.ProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * @Binds rather than @Provides: the implementations already have @Inject
 * constructors, so this only tells Hilt which one satisfies each interface. It
 * generates no factory code of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMealRepository(impl: MealRepositoryImpl): MealRepository

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository
}
