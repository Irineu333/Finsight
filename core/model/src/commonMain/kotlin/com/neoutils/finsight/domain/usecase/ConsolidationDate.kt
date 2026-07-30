package com.neoutils.finsight.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * The date whose rates govern a period's figure: **the end of the period, clamped to today**.
 *
 * Every consolidated figure in the app is a figure *of a period* — a month on the dashboard,
 * a range in the report, an invoice between its opening and its closing — while a rate is
 * dated. The mapping from one to the other is a rule, so it has one owner here rather than a
 * `minOf` repeated in five consumers.
 *
 * The two halves answer different failures:
 * - **the period's end**, so a closed period stays still. December is consolidated at the
 *   rates of December, and a quote recorded today never reaches back and moves a month the
 *   user already read (design D11);
 * - **clamped to today**, because the rates screen dates a rate with a picker and a rate
 *   dated in the future therefore exists. Without the clamp it would govern the current and
 *   every future month — "the last rate on or before the end of the month" asked of a date
 *   that has not happened.
 *
 * [today] is an argument rather than a clock read, and it is meant to be resolved **once per
 * emission**: two figures of the same card governed by dates sampled a moment apart would
 * explain themselves with two different quotes.
 */
fun consolidationDateOf(periodEnd: LocalDate, today: LocalDate): LocalDate =
    minOf(periodEnd, today)

fun consolidationDateOf(month: YearMonth, today: LocalDate): LocalDate =
    consolidationDateOf(month.lastDay, today)
