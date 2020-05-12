package com.github.timgent.sparkdataquality.repository

import com.github.timgent.sparkdataquality.checks.{CheckResult, CheckStatus}
import com.github.timgent.sparkdataquality.checkssuite
import com.github.timgent.sparkdataquality.checkssuite.CheckSuiteStatus.{Error, Success}
import com.github.timgent.sparkdataquality.checkssuite.QcType.{DatasetComparisonQualityCheck, SingleDatasetQualityCheck}
import com.github.timgent.sparkdataquality.checkssuite.{CheckSuiteStatus, ChecksSuiteResult, QcType}
import com.github.timgent.sparkdataquality.utils.CommonFixtures._
import com.sksamuel.elastic4s.testkit.DockerTests
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class ElasticSearchQcResultsRepositoryTest extends AnyWordSpec with Matchers with DockerTests with Eventually {
  "ElasticSearchQcResultsRepository.save" should {
    def generateRawCheckResult(suffix: String, status: CheckStatus) = CheckResult(status, s"checkResult$suffix", s"checkDescription$suffix")

    val someIndex = "index_name"
    implicit val patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 1 second)

    cleanIndex(someIndex)
    "Append check suite results to the index" in {
      val repo: ElasticSearchQcResultsRepository = new ElasticSearchQcResultsRepository(client, someIndex)

      val checkResultA1 = generateRawCheckResult("A1", CheckStatus.Success)
      val checkResultA2 = generateRawCheckResult("A2", CheckStatus.Success)
      val checkResultB1 = generateRawCheckResult("B1", CheckStatus.Error)
      val checkResultB2 = generateRawCheckResult("B2", CheckStatus.Error)
      val checkResultB1Success = generateRawCheckResult("B1", CheckStatus.Error)
      val checkResultB2Success = generateRawCheckResult("B2", CheckStatus.Error)
      val initialResultsToInsert: Seq[ChecksSuiteResult] = Seq(
        ChecksSuiteResult(Success, "checkSuiteA", "resultA", Seq(checkResultA1, checkResultA2), now, SingleDatasetQualityCheck, someTags),
        checkssuite.ChecksSuiteResult(Error, "checkSuiteB", "resultB", Seq(checkResultB1, checkResultB2), now, DatasetComparisonQualityCheck, someTags)
      )
      val moreResultsToInsert: Seq[ChecksSuiteResult] = Seq(
        checkssuite.ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteB", "resultB", Seq(checkResultB1Success, checkResultB2Success),
          now.plusSeconds(10), QcType.DatasetComparisonQualityCheck, someTags)
      )
      def storedResults: Seq[ChecksSuiteResult] = repo.loadAll

      repo.save(initialResultsToInsert)
      eventually(storedResults should contain theSameElementsAs initialResultsToInsert)
      repo.save(moreResultsToInsert)
      eventually(storedResults should contain theSameElementsAs (initialResultsToInsert ++ moreResultsToInsert))
    }
  }
}
