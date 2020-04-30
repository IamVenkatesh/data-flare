package qualitychecker

import java.time.Instant

import qualitychecker.repository.QcResultsRepository

object QualityChecker {
  def doQualityChecks(qualityChecks: Seq[ChecksSuite[_]],
                      metricsRepository: QcResultsRepository,
                      timestamp: Instant): Seq[ChecksSuiteResult[_]] = {
    val qualityCheckResults: Seq[ChecksSuiteResult[_]] = qualityChecks.map(_.run(timestamp))
    metricsRepository.save(qualityCheckResults)
    qualityCheckResults
  }
}
