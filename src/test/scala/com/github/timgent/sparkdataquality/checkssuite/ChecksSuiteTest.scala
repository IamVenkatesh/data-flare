package com.github.timgent.sparkdataquality.checkssuite

import java.time.Instant

import com.amazon.deequ.analyzers.Size
import com.amazon.deequ.analyzers.runners.AnalyzerContext
import com.amazon.deequ.checks.{Check, CheckLevel}
import com.amazon.deequ.metrics.{DoubleMetric, Entity}
import com.amazon.deequ.repository.ResultKey
import com.amazon.deequ.repository.memory.InMemoryMetricsRepository
import com.github.timgent.sparkdataquality.checks.ArbDualDsCheck.DatasetPair
import com.github.timgent.sparkdataquality.checks.CheckDescription.{DualMetricCheckDescription, SingleMetricCheckDescription}
import com.github.timgent.sparkdataquality.checks.DatasourceDescription.{DualDsDescription, SingleDsDescription}
import com.github.timgent.sparkdataquality.checks.QCCheck.SingleDsCheck
import com.github.timgent.sparkdataquality.checks.metrics.{DualMetricCheck, SingleMetricCheck}
import com.github.timgent.sparkdataquality.checks.{ArbDualDsCheck, ArbSingleDsCheck, ArbitraryCheck, CheckResult, CheckStatus, DeequQCCheck, QcType, RawCheckResult}
import com.github.timgent.sparkdataquality.metrics.MetricValue.LongMetric
import com.github.timgent.sparkdataquality.metrics.{MetricComparator, MetricDescriptor, MetricFilter, SimpleMetricDescriptor}
import com.github.timgent.sparkdataquality.repository.{InMemoryMetricsPersister, InMemoryQcResultsRepository}
import com.github.timgent.sparkdataquality.thresholds.AbsoluteThreshold
import com.github.timgent.sparkdataquality.utils.CommonFixtures._
import com.github.timgent.sparkdataquality.utils.TestDataClass
import com.holdenkarau.spark.testing.DatasetSuiteBase
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.util.Success

class ChecksSuiteTest extends AsyncWordSpec with DatasetSuiteBase with Matchers {

  def checkResultAndPersistedResult(
      qcResult: ChecksSuiteResult,
      persistedQcResult: ChecksSuiteResult
  )(
      timestamp: Instant,
      checkSuiteDescription: String,
      checkStatus: CheckSuiteStatus,
      resultDescription: String,
      checkResults: Seq[CheckResult],
      checkTags: Map[String, String]
  ): Assertion = {
    qcResult.timestamp shouldBe timestamp
    qcResult.checkSuiteDescription shouldBe checkSuiteDescription
    qcResult.overallStatus shouldBe checkStatus
    qcResult.checkResults shouldBe checkResults
    qcResult.checkTags shouldBe checkTags
    persistedQcResult.timestamp shouldBe timestamp
    persistedQcResult.checkSuiteDescription shouldBe checkSuiteDescription
    persistedQcResult.overallStatus shouldBe checkStatus
    persistedQcResult.checkResults shouldBe checkResults
    persistedQcResult.checkTags shouldBe checkTags
  }

  import spark.implicits._

  "ChecksSuite" which {
    "has single metric based checks" should {
      "calculate metrics based checks on single datasets" in {
        val ds = Seq(
          NumberString(1, "a"),
          NumberString(2, "b")
        ).toDS
        val checks: Map[DescribedDs, Seq[SingleMetricCheck[LongMetric]]] = Map(
          DescribedDs(ds, datasourceDescription) ->
            Seq(
              SingleMetricCheck
                .sizeCheck(AbsoluteThreshold(Some(2L), Some(2L)), MetricFilter.noFilter)
            )
        )
        val checkSuiteDescription = "my first metricsCheckSuite"
        val metricsBasedChecksSuite =
          ChecksSuite(checkSuiteDescription, singleDsChecks = checks, tags = someTags)

        for {
          checkResults: ChecksSuiteResult <- metricsBasedChecksSuite.run(now)
        } yield {
          checkResults shouldBe ChecksSuiteResult(
            CheckSuiteStatus.Success,
            checkSuiteDescription,
            Seq(
              CheckResult(
                QcType.SingleMetricCheck,
                CheckStatus.Success,
                "Size of 2 was within the range between 2 and 2",
                SingleMetricCheckDescription("SizeCheck", SimpleMetricDescriptor("Size", Some("no filter"))),
                Some(SingleDsDescription(datasourceDescription))
              )
            ),
            now,
            someTags
          )
        }
      }

      "calculate single dataset metrics checks across a number of datasets" in {
        val dsA = Seq(
          NumberString(1, "a"),
          NumberString(2, "b")
        )
        val dsB = Seq(
          NumberString(1, "a"),
          NumberString(2, "b"),
          NumberString(3, "c")
        )
        val checks: Seq[SingleMetricCheck[_]] = Seq(
          SingleMetricCheck.sizeCheck(AbsoluteThreshold(Some(2L), Some(2L)), MetricFilter.noFilter)
        )
        val singleDatasetChecks = Map(
          DescribedDs(dsA.toDS, "dsA") -> checks,
          DescribedDs(dsB.toDS, "dsB") -> checks
        )
        val checkSuiteDescription = "my first metricsCheckSuite"
        val metricsBasedChecksSuite = ChecksSuite(
          checkSuiteDescription,
          singleDsChecks = singleDatasetChecks,
          tags = someTags
        )

        for {
          checkResults: ChecksSuiteResult <- metricsBasedChecksSuite.run(now)
        } yield {
          checkResults shouldBe ChecksSuiteResult(
            CheckSuiteStatus.Error,
            checkSuiteDescription,
            Seq(
              CheckResult(
                QcType.SingleMetricCheck,
                CheckStatus.Success,
                "Size of 2 was within the range between 2 and 2",
                SingleMetricCheckDescription("SizeCheck", SimpleMetricDescriptor("Size", Some("no filter"))),
                Some(SingleDsDescription("dsA"))
              ),
              CheckResult(
                QcType.SingleMetricCheck,
                CheckStatus.Error,
                "Size of 3 was outside the range between 2 and 2",
                SingleMetricCheckDescription("SizeCheck", SimpleMetricDescriptor("Size", Some("no filter"))),
                Some(SingleDsDescription("dsB"))
              )
            ),
            now,
            someTags
          )
        }
      }
    }
    "has dual metric based checks" should {
      "calculate metrics based checks between datasets" in {
        val simpleSizeMetric = MetricDescriptor.SizeMetric()
        val dsA = Seq(
          NumberString(1, "a"),
          NumberString(2, "b"),
          NumberString(3, "c")
        ).toDS
        val dsB = Seq(
          NumberString(1, "a"),
          NumberString(2, "b"),
          NumberString(3, "c")
        ).toDS
        val metricChecks = Seq(
          DualMetricCheck(simpleSizeMetric, simpleSizeMetric, "check size metrics are equal", MetricComparator.metricsAreEqual)
        )
        val dualDatasetChecks = Map(
          DescribedDsPair(DescribedDs(dsA, "dsA"), DescribedDs(dsB, "dsB")) ->
            metricChecks
        )
        val checkSuiteDescription = "my first metricsCheckSuite"
        val metricsBasedChecksSuite = ChecksSuite(
          checkSuiteDescription,
          dualDsChecks = dualDatasetChecks,
          tags = someTags
        )

        for {
          checkResults: ChecksSuiteResult <- metricsBasedChecksSuite.run(now)
        } yield {
          checkResults shouldBe ChecksSuiteResult(
            CheckSuiteStatus.Success,
            checkSuiteDescription,
            Seq(
              CheckResult(
                QcType.DualMetricCheck,
                CheckStatus.Success,
                "metric comparison passed. dsA with LongMetric(3) was compared to dsB with LongMetric(3)",
                DualMetricCheckDescription("check size metrics are equal",
                  SimpleMetricDescriptor("Size",Some("no filter"),None,None),
                  SimpleMetricDescriptor("Size",Some("no filter"),None,None),
                  "metrics are equal"
                ),
                Some(DualDsDescription("dsA", "dsB"))
              )
            ),
            now,
            someTags
          )
        }
      }
    }

    "has any metrics based checks at all" should {
      "store all metrics in a metrics repository" in {
        val simpleSizeMetric = MetricDescriptor.SizeMetric()
        val dsA = Seq(
          NumberString(1, "a"),
          NumberString(2, "b"),
          NumberString(3, "c")
        ).toDS
        val dsB = Seq(
          NumberString(1, "a"),
          NumberString(2, "b")
        ).toDS
        val dsC = Seq(
          NumberString(1, "a")
        ).toDS
        val dualMetricChecks = Seq(
          DualMetricCheck(simpleSizeMetric, simpleSizeMetric, "check size metrics are equal", MetricComparator.metricsAreEqual)
        )
        val dualDatasetChecks = Map(DescribedDsPair(DescribedDs(dsA, "dsA"), DescribedDs(dsB, "dsB")) -> dualMetricChecks)
        val singleDatasetChecks: Map[DescribedDs, Seq[SingleMetricCheck[LongMetric]]] = Map(
          DescribedDs(dsC, "dsC") ->
            Seq(
              SingleMetricCheck
                .sizeCheck(AbsoluteThreshold(Some(2L), Some(2L)), MetricFilter.noFilter)
            )
        )
        val checkSuiteDescription = "my first metricsCheckSuite"
        val inMemoryMetricsPersister = new InMemoryMetricsPersister
        val metricsBasedChecksSuite = ChecksSuite(
          checkSuiteDescription,
          singleDsChecks = singleDatasetChecks,
          tags = someTags,
          dualDsChecks = dualDatasetChecks,
          metricsPersister = inMemoryMetricsPersister
        )

        for {
          _ <- metricsBasedChecksSuite.run(now)
          storedMetrics <- inMemoryMetricsPersister.loadAll
        } yield storedMetrics shouldBe Map(
          now -> Map(
            SingleDsDescription("dsA") -> Map(
              simpleSizeMetric.toSimpleMetricDescriptor -> LongMetric(3L)
            ),
            SingleDsDescription("dsB") -> Map(
              simpleSizeMetric.toSimpleMetricDescriptor -> LongMetric(2L)
            ),
            SingleDsDescription("dsC") -> Map(
              simpleSizeMetric.toSimpleMetricDescriptor -> LongMetric(1L)
            )
          )
        )
      }
    }

    "contains deequ checks" should {
      "be able to do the deequ quality checks and store check results and underlying metrics in a repository" in {
        val testDataset = DescribedDs(
          List((1, "a"), (2, "b"), (3, "c")).map(TestDataClass.tupled).toDF,
          "testDataset"
        )
        val qcResultsRepository = new InMemoryQcResultsRepository
        val deequMetricsRepository: InMemoryMetricsRepository = new InMemoryMetricsRepository

        val deequQcConstraint = DeequQCCheck(Check(CheckLevel.Error, "size check").hasSize(_ == 3))
        val qualityChecks = ChecksSuite(
          checkSuiteDescription = "sample deequ checks",
          singleDsChecks = Map(testDataset -> Seq(deequQcConstraint)),
          tags = someTags,
          deequMetricsRepository = deequMetricsRepository,
          qcResultsRepository = qcResultsRepository
        )

        for {
          qcResults <- qualityChecks.run(now)
          persistedQcResults <- qcResultsRepository.loadAll
          persistedDeequMetrics = deequMetricsRepository.load().get()
        } yield {
          persistedQcResults.size shouldBe 1
          checkResultAndPersistedResult(qcResults, persistedQcResults.head)(
            timestamp = now,
            checkSuiteDescription = "sample deequ checks",
            checkStatus = CheckSuiteStatus.Success,
            resultDescription = "1 checks were successful. 0 checks gave errors. 0 checks gave warnings",
            checkResults = Seq(
              CheckResult(
                QcType.DeequQualityCheck,
                CheckStatus.Success,
                "Deequ check was successful",
                deequQcConstraint.description
              )
            ),
            checkTags = someTags
          )

          persistedDeequMetrics.size shouldBe 1
          persistedDeequMetrics.head.resultKey shouldBe ResultKey(now.toEpochMilli, Map.empty)
          persistedDeequMetrics.head.analyzerContext shouldBe AnalyzerContext(
            Map(
              Size(None) -> DoubleMetric(Entity.Dataset, "Size", "*", Success(3.0))
            )
          )
        }
      }
    }

    "contains single dataset checks" should {
      "run custom single table checks and store results in a repository" in {
        val testDataset = List((1, "a"), (2, "b"), (3, "c")).map(TestDataClass.tupled).toDS
        val qcResultsRepository = new InMemoryQcResultsRepository
        val checkDescription = "DB: X, table: Y"

        val singleDatasetCheck = ArbSingleDsCheck("someSingleDatasetCheck") { dataset =>
          RawCheckResult(CheckStatus.Error, "someSingleDatasetCheck was not successful")
        }
        val checks = Seq(singleDatasetCheck)

        val qualityChecks = ChecksSuite(
          checkDescription,
          singleDsChecks = Map(DescribedDs(testDataset, datasourceDescription) -> checks),
          tags = someTags,
          qcResultsRepository = qcResultsRepository
        )

        for {
          qcResults: ChecksSuiteResult <- qualityChecks.run(now)
          persistedQcResults: Seq[ChecksSuiteResult] <- qcResultsRepository.loadAll
        } yield {
          checkResultAndPersistedResult(qcResults, persistedQcResults.head)(
            timestamp = now,
            checkSuiteDescription = "DB: X, table: Y",
            checkStatus = CheckSuiteStatus.Error,
            resultDescription = "0 checks were successful. 1 checks gave errors. 0 checks gave warnings",
            checkResults = Seq(
              CheckResult(
                QcType.ArbSingleDsCheck,
                CheckStatus.Error,
                "someSingleDatasetCheck was not successful",
                singleDatasetCheck.description,
                Some(SingleDsDescription(datasourceDescription))
              )
            ),
            checkTags = someTags
          )
        }
      }
    }

    "contains dual dataset checks" should {
      "run custom 2 table checks and store results in a repository" in {
        val testDataset = DescribedDs(
          List((1, "a"), (2, "b"), (3, "c")).map(TestDataClass.tupled).toDS,
          "testDataset"
        )
        val datasetToCompare = DescribedDs(
          List((1, "a"), (2, "b"), (3, "c"), (4, "d")).map(TestDataClass.tupled).toDS,
          "datasetToCompare"
        )
        val datasetPair = DescribedDsPair(testDataset, datasetToCompare)
        val qcResultsRepository = new InMemoryQcResultsRepository

        val datasetComparisonCheck = ArbDualDsCheck("Table counts equal") {
          case DatasetPair(ds, dsToCompare) =>
            RawCheckResult(CheckStatus.Error, "counts were not equal")
        }
        val qualityChecks = ChecksSuite(
          "table A vs table B comparison",
          dualDsChecks = Map(datasetPair -> Seq(datasetComparisonCheck)),
          tags = someTags,
          qcResultsRepository = qcResultsRepository
        )

        for {
          qcResults: ChecksSuiteResult <- qualityChecks.run(now)
          persistedQcResults: Seq[ChecksSuiteResult] <- qcResultsRepository.loadAll
        } yield {
          checkResultAndPersistedResult(qcResults, persistedQcResults.head)(
            timestamp = now,
            checkSuiteDescription = "table A vs table B comparison",
            checkStatus = CheckSuiteStatus.Error,
            resultDescription = "0 checks were successful. 1 checks gave errors. 0 checks gave warnings",
            checkResults = Seq(
              CheckResult(
                QcType.ArbDualDsCheck,
                CheckStatus.Error,
                "counts were not equal",
                datasetComparisonCheck.description,
                Some(DualDsDescription("testDataset", "datasetToCompare"))
              )
            ),
            checkTags = someTags
          )
        }
      }
    }

    "contains arbitrary checks" should {
      "be able to run completely arbitrary checks and store results in a repository" in {
        val qcResultsRepository = new InMemoryQcResultsRepository

        val arbitraryCheck = ArbitraryCheck("some arbitrary check") {
          RawCheckResult(CheckStatus.Error, "The arbitrary check failed!")
        }
        val qualityChecks = ChecksSuite(
          "table A, table B, and table C comparison",
          arbitraryChecks = Seq(arbitraryCheck),
          tags = someTags,
          qcResultsRepository = qcResultsRepository
        )

        for {
          qcResults: ChecksSuiteResult <- qualityChecks.run(now)
          persistedQcResults: Seq[ChecksSuiteResult] <- qcResultsRepository.loadAll
        } yield {
          qcResults.timestamp shouldBe now
          qcResults.checkSuiteDescription shouldBe "table A, table B, and table C comparison"
          qcResults.overallStatus shouldBe CheckSuiteStatus.Error

          persistedQcResults.size shouldBe 1
          persistedQcResults.head.timestamp shouldBe now
          persistedQcResults.head.checkSuiteDescription shouldBe "table A, table B, and table C comparison"
          persistedQcResults.head.overallStatus shouldBe CheckSuiteStatus.Error

          checkResultAndPersistedResult(qcResults, persistedQcResults.head)(
            timestamp = now,
            checkSuiteDescription = "table A, table B, and table C comparison",
            checkStatus = CheckSuiteStatus.Error,
            resultDescription = "0 checks were successful. 1 checks gave errors. 0 checks gave warnings",
            checkResults = Seq(
              CheckResult(
                QcType.ArbitraryCheck,
                CheckStatus.Error,
                "The arbitrary check failed!",
                arbitraryCheck.description
              )
            ),
            checkTags = someTags
          )
        }
      }
    }
  }
}
