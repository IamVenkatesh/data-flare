package com.github.timgent.sparkdataquality.utils

import java.time.Instant

import com.github.timgent.sparkdataquality.checks.CheckStatus.Success
import com.github.timgent.sparkdataquality.checks.QCCheck.SingleDatasetCheck
import com.github.timgent.sparkdataquality.checks.RawCheckResult

object CommonFixtures {
  val now = Instant.now
  val someCheck = SingleDatasetCheck("some check")(_ => RawCheckResult(Success, "successful"))
  val someTags = Map("project" -> "project A")
}
