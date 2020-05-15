package com.github.timgent.sparkdataquality.checks

import com.github.timgent.sparkdataquality.checks.QCCheck.MetricsBasedCheck.SizeCheck
import com.github.timgent.sparkdataquality.checks.QCCheck.SingleDatasetCheck.sumOfValuesCheck
import com.github.timgent.sparkdataquality.metrics.MetricFilter
import com.github.timgent.sparkdataquality.metrics.MetricValue.{DoubleMetric, LongMetric}
import com.github.timgent.sparkdataquality.thresholds.AbsoluteThreshold
import com.github.timgent.sparkdataquality.utils.CommonFixtures.NumberString
import com.github.timgent.sparkdataquality.utils.TestDataClass
import com.holdenkarau.spark.testing.DatasetSuiteBase
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.apache.spark.sql.functions.lit

class QCCheckTest extends AnyWordSpec with DatasetSuiteBase with Matchers {

  import spark.implicits._

  "sumOfValuesCheck" should {
    val columnName = "number"
    lazy val dsWithNumberSumOf6 = List((1, "a"), (2, "b"), (3, "c")).map(TestDataClass.tupled).toDS

    def expectedResultDescription(passed: Boolean, threshold: AbsoluteThreshold[Long]) = if (passed)
      s"Sum of column number was 6, which was within the threshold $threshold"
    else
      s"Sum of column number was 6, which was outside the threshold $threshold"
    "pass the qc check" when {
      "sum of values is above a lower bound" in {
        val threshold = AbsoluteThreshold(Some(5L), None)
        val result: CheckResult = sumOfValuesCheck(columnName, threshold).applyCheck(dsWithNumberSumOf6)
        result.status shouldBe CheckStatus.Success
        result.resultDescription shouldBe expectedResultDescription(passed = true, threshold)
      }

      "sum of values is below an upper bound" in {
        val threshold = AbsoluteThreshold(None, Some(7L))
        val result: CheckResult = sumOfValuesCheck(columnName, threshold).applyCheck(dsWithNumberSumOf6)
        result.status shouldBe CheckStatus.Success
        result.resultDescription shouldBe expectedResultDescription(passed = true, threshold)
      }

      "sum of values is within both bounds" in {
        val threshold = AbsoluteThreshold(Some(5L), Some(7L))
        val result: CheckResult = sumOfValuesCheck(columnName, threshold).applyCheck(dsWithNumberSumOf6)
        result.status shouldBe CheckStatus.Success
        result.resultDescription shouldBe expectedResultDescription(passed = true, threshold)
      }
    }

    "fail the qc check" when {
      "sum of values is below a lower bound" in {
        val threshold = AbsoluteThreshold(Some(7L), None)
        val result: CheckResult = sumOfValuesCheck(columnName, threshold).applyCheck(dsWithNumberSumOf6)
        result.status shouldBe CheckStatus.Error
        result.resultDescription shouldBe expectedResultDescription(passed = false, threshold)
      }
      "sum of values is above an upper bound" in {
        val threshold = AbsoluteThreshold(None, Some(5L))
        val result: CheckResult = sumOfValuesCheck(columnName, threshold).applyCheck(dsWithNumberSumOf6)
        result.status shouldBe CheckStatus.Error
        result.resultDescription shouldBe expectedResultDescription(passed = false, threshold)
      }
    }

  }

  "MetricsBasedCheck for any check type" should {
    val simpleSizeCheck = SizeCheck(AbsoluteThreshold(Some(0), Some(3)), MetricFilter.noFilter)

    "apply the check when the required metric is provided in the metrics map" in {
      val result = simpleSizeCheck.applyCheckOnMetrics(Map(simpleSizeCheck.metricDescriptor -> LongMetric(2)))
      result.status shouldBe CheckStatus.Success
    }

    "fail when the required metric is not provided in the metrics map" in {
      val result = simpleSizeCheck.applyCheckOnMetrics(Map.empty)
      result.status shouldBe CheckStatus.Error
      result.resultDescription shouldBe "Failed to find corresponding metric for this check. Please report this error - this should not occur"
    }

    "fail when the required metric is the wrong type" in {
      val result = simpleSizeCheck.applyCheckOnMetrics(Map(simpleSizeCheck.metricDescriptor -> DoubleMetric(2)))
      result.status shouldBe CheckStatus.Error
      result.resultDescription shouldBe "Found metric of the wrong type for this check. Please report this error - this should not occur"
    }
  }

  "MetricsBasedCheck.SizeCheck" should {
    "pass a check where the size is within the threshold" in {
      val result: CheckResult = SizeCheck(AbsoluteThreshold(Some(0), Some(3)), MetricFilter.noFilter).applyCheck(LongMetric(2))
      result shouldBe CheckResult(CheckStatus.Success, "Size of 2 was within the range between 0 and 3", "SizeCheck with filter: no filter")
    }

    "fail a check where the size is outside the threshold" in {
      val result: CheckResult = SizeCheck(AbsoluteThreshold(Some(0), Some(3)), MetricFilter(lit(false), "someFilter")).applyCheck(LongMetric(4))
      result shouldBe CheckResult(CheckStatus.Error, "Size of 4 was outside the range between 0 and 3", "SizeCheck with filter: someFilter")
    }
  }
}
