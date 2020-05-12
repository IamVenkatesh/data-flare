package com.github.timgent.sparkdataquality.checkssuite

import java.time.Instant

import com.github.timgent.sparkdataquality.checks.CheckResult
import com.github.timgent.sparkdataquality.checks.QCCheck.SingleDatasetCheck
import com.github.timgent.sparkdataquality.checkssuite.ChecksSuite.getOverallCheckResultDescription
import org.apache.spark.sql.Dataset

object SingleDatasetChecksSuite {
  def apply(ds: Dataset[_],
            checkDesc: String,
            checks: Seq[SingleDatasetCheck],
            checkTags: Map[String, String],
            checkResultCombiner: Seq[CheckResult] => CheckSuiteStatus = ChecksSuiteResultStatusCalculator.getWorstCheckStatus
           ): SingleDatasetChecksSuite = {
    new SingleDatasetChecksSuite {
      def run(timestamp: Instant): ChecksSuiteResult = {
        val checkResults: Seq[CheckResult] = checks.map(_.applyCheck(dataset))
        val overallCheckStatus = checkResultCombiner(checkResults)
        ChecksSuiteResult(overallCheckStatus, checkSuiteDescription, getOverallCheckResultDescription(checkResults),
          checkResults, timestamp, qcType, checkTags)
      }

      override def dataset: Dataset[_] = ds

      override def checkSuiteDescription: String = checkDesc

      override def qcType: QcType = QcType.SingleDatasetQualityCheck
    }
  }
}

trait SingleDatasetChecksSuite extends ChecksSuite {
  def dataset: Dataset[_]
}